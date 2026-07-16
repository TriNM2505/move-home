package vn.movehome.backend.incident;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dispute.CustomerRefundService;
import vn.movehome.backend.driver.finance.DriverWallet;
import vn.movehome.backend.driver.finance.DriverWalletRepository;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.AuditService;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Manager xu ly su co van chuyen do tai xe bao (V44).
 * - confirm: xac nhan su co -> ban don lai pool (CONFIRMED, driver_id=NULL) + bao khach doi tai xe moi.
 * - compensate: qua 15 phut khong ai nhan -> hoan coc 30% + boi thuong 200k ve customer_wallet;
 *   rieng 200k tru vao tai xe gay su co (cọc -> vi -> SUSPENDED, HR-18). Cong ty chiu phan coc.
 * - resolveReassigned: goi khi tai xe khac nhan lai don -> dong su co (RESOLVED_REASSIGNED).
 * RBAC: /api/manager/** da gioi han MANAGER o SecurityConfig (HR-10).
 * AC-08 tien BigDecimal scale 0; AC-13 moi lan dong tien ghi transaction; HR-18 vi khong am.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerIncidentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.3000");
    // Muc coc chuan tai xe (CONTEXT §2) — de tinh so tien nap lai khi bi khoa.
    private static final BigDecimal DEPOSIT_TARGET = new BigDecimal("3000000");
    private static final String CONFIRMED = "CONFIRMED";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String CANCELLED = "CANCELLED";

    // So phut cho tai xe khac nhan lai truoc khi cho phep hoan coc + boi thuong (demo co the ha).
    @Value("${app.incident.reassign-minutes:15}")
    private long reassignMinutes;
    // Muc boi thuong co dinh cho khach khi khong tim duoc tai xe thay the.
    @Value("${app.incident.compensation:200000}")
    private long compensationAmountVnd;

    private final DriverIncidentReportRepository reportRepository;
    private final DriverIncidentPhotoService photoService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CustomerRefundService customerRefundService;
    private final DriverWalletRepository driverWalletRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // ===================== QUERY =====================

    @Transactional(readOnly = true)
    public Page<IncidentListItem> list(String status, int page, int size) {
        PageRequest pageable = createPageRequest(page, size);
        String normalized = normalizeStatus(status);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Page<DriverIncidentReport> rows = (normalized == null)
                ? reportRepository.findAllByOrderByCreatedAtDesc(pageable)
                : reportRepository.findByStatusOrderByCreatedAtAsc(normalized, pageable);

        return rows.map(r -> {
            ServiceOrder order = orderRepository.findById(r.getOrderId()).orElse(null);
            User driver = userRepository.findById(r.getDriverId()).orElse(null);
            return new IncidentListItem(
                    r.getId(),
                    r.getOrderId(),
                    order != null ? order.getOrderCode() : null,
                    r.getDriverId(),
                    driver != null ? driver.getFullName() : null,
                    r.getReason(),
                    r.getStatus(),
                    r.getOrderStatusSnapshot(),
                    r.getReassignDeadline(),
                    isOverdue(r, now),
                    r.getCreatedAt(),
                    r.getConfirmedAt(),
                    r.getRefundAmount(),
                    r.getPenaltyAmount());
        });
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse detail(UUID id) {
        DriverIncidentReport r = reportRepository.findById(id).orElseThrow(this::notFound);
        ServiceOrder order = orderRepository.findById(r.getOrderId()).orElse(null);
        User driver = userRepository.findById(r.getDriverId()).orElse(null);
        User customer = order != null ? userRepository.findById(order.getCustomerId()).orElse(null) : null;
        List<String> photos = photoService.signedUrls(r.getId());

        return new IncidentDetailResponse(
                r.getId(),
                r.getOrderId(),
                order != null ? order.getOrderCode() : null,
                order != null ? order.getStatus() : null,
                r.getOrderStatusSnapshot(),
                r.getDriverId(),
                driver != null ? driver.getFullName() : null,
                driver != null ? driver.getPhone() : null,
                order != null ? order.getCustomerId() : null,
                customer != null ? customer.getFullName() : null,
                customer != null ? customer.getPhone() : null,
                r.getReason(),
                r.getStatus(),
                r.getReassignDeadline(),
                isOverdue(r, OffsetDateTime.now(ZoneOffset.UTC)),
                r.getConfirmedAt(),
                order != null ? depositOf(order) : null,
                BigDecimal.valueOf(compensationAmountVnd).setScale(0),
                r.getRefundAmount(),
                r.getPenaltyAmount(),
                r.getCreatedAt(),
                photos,
                order == null ? null : new IncidentDetailResponse.OrderSummary(
                        order.getPickupAddress(),
                        order.getDropoffAddress(),
                        order.getTotalQuote(),
                        order.getScheduledAt()));
    }

    // ===================== CONFIRM (ban don lai pool) =====================

    /**
     * Manager xac nhan su co: don ve CONFIRMED (driver_id = NULL) de tai xe khac nhan lai;
     * bao khach doi tai xe moi + moc 15 phut. Set truc tiep status (KHONG publish event) de tranh
     * gui notification "don xac nhan" gay hieu nham — khach da co thong bao rieng.
     */
    @Transactional
    public IncidentDetailResponse confirm(UUID id, User actor) {
        DriverIncidentReport report = findForUpdate(id);
        if (!DriverIncidentReport.STATUS_PENDING.equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INCIDENT_ALREADY_PROCESSED|Sự cố đã được xử lý.");
        }

        ServiceOrder order = orderRepository.findByIdForUpdate(report.getOrderId())
                .filter(o -> o.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        // Don phai con dung tai xe bao su co va o dung trang thai (chong thao tac tranh chap).
        if (!report.getDriverId().equals(order.getDriverId())
                || (!ACCEPTED.equals(order.getStatus()) && !IN_PROGRESS.equals(order.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ORDER_STATE_CHANGED|Đơn đã đổi trạng thái, không thể xác nhận sự cố.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Ban don lai pool: xoa tai xe + reset moc chuyen, dua ve CONFIRMED (dieu kien pool o acceptOrder).
        order.setDriverId(null);
        order.setArrivedAt(null);
        order.setStartedAt(null);
        order.setStatus(CONFIRMED);
        orderRepository.saveAndFlush(order);

        report.setStatus(DriverIncidentReport.STATUS_CONFIRMED);
        report.setConfirmedBy(actor.getId());
        report.setConfirmedAt(now);
        report.setReassignDeadline(now.plusMinutes(reassignMinutes));
        reportRepository.saveAndFlush(report);

        auditService.log(actor.getId(), actor.getEmail(), "INCIDENT_CONFIRMED",
                "DRIVER_INCIDENT", report.getId().toString(),
                "{\"order_code\":\"" + order.getOrderCode() + "\",\"reassign_deadline\":\""
                        + report.getReassignDeadline() + "\"}");

        // Bao khach: dang tim tai xe moi, neu 15 phut khong co se hoan coc + boi thuong.
        safeNotify(order.getCustomerId(), NotificationType.ORDER_REASSIGNING,
                "Đơn hàng đang tìm tài xế mới",
                "Xin lỗi anh/chị, đơn hàng của anh chị tài xế đang có vấn đề không thể tiếp tục được. "
                        + "Mong anh chị vui lòng đợi tài xế mới nhận đơn và tiếp tục. Nếu sau 15 phút nữa "
                        + "không có tài xế nào nhận, chúng tôi sẽ hoàn cọc và bồi thường theo chính sách "
                        + "cho quý khách. Cảm ơn quý khách.");
        // Bao tai xe bao su co: da duoc xac nhan, don chuyen cho tai xe khac.
        safeNotify(report.getDriverId(), NotificationType.DRIVER_INCIDENT_REPORTED,
                "Sự cố đã được xác nhận",
                "Sự cố đơn " + order.getOrderCode() + " đã được ghi nhận. Đơn được chuyển cho tài xế khác. "
                        + "Bạn có thể nhận đơn mới.");

        log.info("incident_confirmed actor_id={} order_code={} incident_id={} deadline={}",
                actor.getId(), order.getOrderCode(), report.getId(), report.getReassignDeadline());
        return detail(report.getId());
    }

    // ===================== COMPENSATE (qua 15 phut, khong ai nhan) =====================

    /**
     * Qua han 15 phut ma chua co tai xe nhan lai: hoan coc 30% + boi thuong 200k ve customer_wallet;
     * tru 200k vao tai xe gay su co (coc -> vi -> SUSPENDED). Don ve CANCELLED (COMPANY).
     */
    @Transactional
    public IncidentDetailResponse compensate(UUID id, User actor) {
        DriverIncidentReport report = findForUpdate(id);
        if (!DriverIncidentReport.STATUS_CONFIRMED.equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INCIDENT_NOT_CONFIRMED|Chỉ hoàn cọc + bồi thường cho sự cố đã xác nhận.");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (report.getReassignDeadline() == null || now.isBefore(report.getReassignDeadline())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "REASSIGN_WINDOW_NOT_OVER|Chưa quá 15 phút chờ tài xế mới, chưa thể hoàn cọc.");
        }

        ServiceOrder order = orderRepository.findByIdForUpdate(report.getOrderId())
                .filter(o -> o.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        // Neu da co tai xe nhan lai (don khong con o pool) -> khong hoan coc.
        if (!CONFIRMED.equals(order.getStatus()) || order.getDriverId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ORDER_ALREADY_REASSIGNED|Đơn đã có tài xế nhận lại, không thể hoàn cọc.");
        }

        BigDecimal deposit = depositOf(order);
        BigDecimal compensation = BigDecimal.valueOf(compensationAmountVnd).setScale(0);
        BigDecimal refundToCustomer = deposit.add(compensation).setScale(0);

        // 1) Hoan coc 30% + boi thuong 200k ve vi khach (1 giao dich REFUND, AC-13, HR-18).
        customerRefundService.refundForCancellation(order.getCustomerId(), order.getId(),
                refundToCustomer, "Hoàn cọc + bồi thường sự cố vận chuyển đơn " + order.getOrderCode());

        // 2) Tru 200k vao tai xe gay su co: coc -> vi -> SUSPENDED neu thieu.
        BigDecimal penaltyCollected = deductPenaltyFromDriver(
                report.getDriverId(), order.getId(), order.getOrderCode(), compensation);

        // 3) Don ve CANCELLED (COMPANY). Set truc tiep + audit (giong cac luong huy khac, khong publish event).
        order.setStatus(CANCELLED);
        order.setCancelledAt(now);
        order.setCancellationReason("Sự cố vận chuyển: không tìm được tài xế thay thế trong "
                + reassignMinutes + " phút (cancelled_by=COMPANY).");
        orderRepository.saveAndFlush(order);

        report.setStatus(DriverIncidentReport.STATUS_COMPENSATED);
        report.setRefundAmount(refundToCustomer);
        report.setPenaltyAmount(penaltyCollected);
        report.setCompensatedBy(actor.getId());
        report.setCompensatedAt(now);
        reportRepository.saveAndFlush(report);

        auditService.log(actor.getId(), actor.getEmail(), "INCIDENT_COMPENSATED",
                "DRIVER_INCIDENT", report.getId().toString(),
                "{\"order_code\":\"" + order.getOrderCode() + "\",\"refund\":" + refundToCustomer.toPlainString()
                        + ",\"penalty_collected\":" + penaltyCollected.toPlainString() + "}");

        safeNotify(order.getCustomerId(), NotificationType.ORDER_INCIDENT_REFUNDED,
                "Đã hoàn cọc và bồi thường",
                "Đơn " + order.getOrderCode() + ": do không tìm được tài xế thay thế, chúng tôi đã hoàn "
                        + refundToCustomer.toPlainString() + " VND (bao gồm cọc và bồi thường "
                        + compensation.toPlainString() + " VND) vào ví của bạn. Rất xin lỗi vì sự bất tiện.");
        safeNotify(report.getDriverId(), NotificationType.PENALTY_WALLET_DEDUCTED,
                "Bị trừ tiền do sự cố vận chuyển",
                "Đơn " + order.getOrderCode() + ": bạn bị trừ " + penaltyCollected.toPlainString()
                        + " VND để bồi thường cho khách do không thể tiếp tục giao.");

        log.info("incident_compensated actor_id={} order_code={} refund={} penalty={}",
                actor.getId(), order.getOrderCode(), refundToCustomer, penaltyCollected);
        return detail(report.getId());
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    // ===================== helpers =====================

    private boolean isOverdue(DriverIncidentReport r, OffsetDateTime now) {
        return DriverIncidentReport.STATUS_CONFIRMED.equals(r.getStatus())
                && r.getReassignDeadline() != null
                && now.isAfter(r.getReassignDeadline());
    }

    /** Coc = 30% total (commission snapshot), VND nguyen dong lam tron xuong (AC-08). */
    private BigDecimal depositOf(ServiceOrder order) {
        BigDecimal total = order.getTotalQuote() != null ? order.getTotalQuote() : BigDecimal.ZERO;
        BigDecimal rate = order.getCommissionRateSnapshot() != null
                ? order.getCommissionRateSnapshot() : DEFAULT_COMMISSION_RATE;
        return total.multiply(rate).setScale(0, RoundingMode.FLOOR);
    }

    /**
     * Tru tien phat cua tai xe theo thu tu: COC (driver_profile.deposit_amount) -> VI (driver_wallet)
     * -> SUSPENDED neu van thieu. HR-18: chi tru min(so du, con lai), khong bao gio am.
     * AC-13: moi lan tru ghi 1 transaction DAMAGE_DEDUCTION (amount am).
     * @return tong so tien thu duoc tu tai xe.
     */
    private BigDecimal deductPenaltyFromDriver(UUID driverId, UUID orderId, String orderCode, BigDecimal penalty) {
        BigDecimal remaining = money(penalty);

        // 1) Tru tu COC (collateral 3 trieu) truoc.
        BigDecimal depositPart = BigDecimal.ZERO.setScale(0);
        DriverProfile profile = driverProfileRepository.findByUserId(driverId).orElse(null);
        if (profile != null && remaining.signum() > 0) {
            BigDecimal deposit = moneyOrZero(profile.getDepositAmount());
            depositPart = deposit.min(remaining);
            if (depositPart.signum() > 0) {
                profile.setDepositAmount(deposit.subtract(depositPart).setScale(0));
                driverProfileRepository.saveAndFlush(profile);
                transactionRepository.saveAndFlush(Transaction.builder()
                        .userId(driverId)
                        .type(TransactionType.DAMAGE_DEDUCTION)
                        .amount(depositPart.negate())
                        .relatedOrderId(orderId)
                        .description("Phạt sự cố vận chuyển đơn " + orderCode + " (trừ cọc)")
                        .build());
                remaining = remaining.subtract(depositPart).setScale(0);
            }
        }

        // 2) Con thieu -> tru tu VI (driver_wallet.balance).
        BigDecimal walletPart = BigDecimal.ZERO.setScale(0);
        if (remaining.signum() > 0) {
            driverWalletRepository.insertIfMissing(driverId);
            DriverWallet wallet = driverWalletRepository.findByDriverIdForUpdate(driverId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "DRIVER_WALLET_NOT_FOUND|Không tìm thấy ví tài xế."));
            BigDecimal balance = moneyOrZero(wallet.getBalance());
            walletPart = balance.min(remaining);
            if (walletPart.signum() > 0) {
                BigDecimal after = balance.subtract(walletPart).setScale(0);
                wallet.setBalance(after);
                driverWalletRepository.saveAndFlush(wallet);
                transactionRepository.saveAndFlush(Transaction.builder()
                        .userId(driverId)
                        .type(TransactionType.DAMAGE_DEDUCTION)
                        .amount(walletPart.negate())
                        .relatedOrderId(orderId)
                        .balanceAfter(after)
                        .description("Phạt sự cố vận chuyển đơn " + orderCode + " (trừ ví)")
                        .build());
                remaining = remaining.subtract(walletPart).setScale(0);
            }
        }

        // 3) Van thieu -> khoa tai khoan tai xe, yeu cau nap lai coc.
        if (remaining.signum() > 0) {
            suspendDriverForPenalty(driverId, orderCode, remaining);
        }
        return depositPart.add(walletPart).setScale(0);
    }

    // Khoa tai khoan tai xe vi thieu tien phat — luu ly do kem so tien can nap lai cho du coc.
    private void suspendDriverForPenalty(UUID driverId, String orderCode, BigDecimal uncovered) {
        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null || driver.getStatus() == UserStatus.SUSPENDED) {
            return;
        }
        BigDecimal depositRemaining = driverProfileRepository.findByUserId(driverId)
                .map(DriverProfile::getDepositAmount)
                .map(this::moneyOrZero)
                .orElse(BigDecimal.ZERO.setScale(0));
        BigDecimal topUpNeeded = DEPOSIT_TARGET.subtract(depositRemaining).max(BigDecimal.ZERO).setScale(0);

        String reason = "Thiếu tiền bồi thường sự cố vận chuyển đơn " + orderCode + ". "
                + "Cần nạp " + topUpNeeded.toPlainString() + " VND để khôi phục đủ tiền cọc 3.000.000 VND."
                + (uncovered.signum() > 0
                        ? " Còn nợ " + uncovered.toPlainString() + " VND tiền bồi thường chưa thu được."
                        : "");

        driver.setSuspensionPreviousStatus(driver.getStatus());
        driver.setStatus(UserStatus.SUSPENDED);
        driver.setSuspendedAt(OffsetDateTime.now(ZoneOffset.UTC).toInstant());
        driver.setSuspensionReason(reason);
        userRepository.saveAndFlush(driver);

        safeNotify(driverId, NotificationType.PENALTY_ACCOUNT_LOCKED,
                "Tài khoản bị khóa do thiếu tiền bồi thường", reason);
    }

    private DriverIncidentReport findForUpdate(UUID id) {
        return reportRepository.findByIdForUpdate(id).orElseThrow(this::notFound);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "INCIDENT_NOT_FOUND|Không tìm thấy sự cố vận chuyển.");
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        boolean valid = normalized.equals(DriverIncidentReport.STATUS_PENDING)
                || normalized.equals(DriverIncidentReport.STATUS_CONFIRMED)
                || normalized.equals(DriverIncidentReport.STATUS_RESOLVED_REASSIGNED)
                || normalized.equals(DriverIncidentReport.STATUS_COMPENSATED);
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Trạng thái sự cố không hợp lệ.");
        }
        return normalized;
    }

    private PageRequest createPageRequest(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Số trang không hợp lệ.");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.");
        }
        return PageRequest.of(page, size);
    }

    private void safeNotify(UUID userId, String type, String title, String message) {
        if (userId == null) {
            return;
        }
        try {
            notificationService.create(userId, type, title, message);
        } catch (Exception ex) {
            log.warn("Khong the tao notification {} cho user {}: {}", type, userId, ex.getMessage());
        }
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(0);
        }
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(0) : value.setScale(0, RoundingMode.HALF_UP);
    }
}
