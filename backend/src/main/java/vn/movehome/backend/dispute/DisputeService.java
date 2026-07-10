package vn.movehome.backend.dispute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.DriverEarningService;
import vn.movehome.backend.driver.finance.DriverWallet;
import vn.movehome.backend.driver.finance.DriverWalletRepository;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.AuditService;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private static final String ORDER_COMPLETED = "COMPLETED";
    private static final String ORDER_DISPUTED = "DISPUTED";
    private static final String ORDER_IN_DISPUTE = "IN_DISPUTE";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> OPEN_STATUSES = Set.of(DisputeStatus.OPEN, DisputeStatus.INVESTIGATING);
    private static final Set<String> VALID_CLAIM_TYPES = Set.of(
            "DAMAGE",
            "MISSING_ITEM",
            "LATE_DELIVERY",
            "INAPPROPRIATE_BEHAVIOR",
            "OTHER");
    private static final Set<String> DISPUTED_ORDER_STATUSES = Set.of(ORDER_DISPUTED, ORDER_IN_DISPUTE);

    // Cua so tai xe nop bo sung tien phat truoc khi bi khoa + tru coc. 5 phut de demo (leader chot 2026-07-10).
    private static final Duration PENALTY_TOP_UP_WINDOW = Duration.ofMinutes(5);
    // Muc coc chuan cua tai xe (CONTEXT §2 Driver Deposit) — dung de tinh so tien can nap lai khi bi khoa
    private static final BigDecimal DEPOSIT_TARGET = new BigDecimal("3000000");

    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusTransitionService orderStatusTransitionService;
    private final UserRepository userRepository;
    private final CustomerRefundService customerRefundService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final DriverEarningService driverEarningService;
    private final DriverWalletRepository driverWalletRepository;
    private final TransactionRepository transactionRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DisputePhotoService disputePhotoService;

    // Cua so khieu nai = cua so escrow (CONTEXT §2: khach chi khieu nai trong 2h sau COMPLETED,
    // truoc khi 70% duoc nha cho tai xe). Dung chung config voi EscrowReleaseService.
    // Field injection (khong final) de khong doi constructor — test tu bo qua check khi = 0.
    @Value("${app.escrow.hold-minutes:120}")
    private long escrowHoldMinutes;

    @Transactional
    public DisputeActionResponse create(UUID orderId, User customer, CreateDisputeRequest request) {
        requireUser(customer);
        String claimType = normalizeClaimType(request.claimType());
        String statement = normalizeText(request.customerStatement(), "INVALID_CUSTOMER_STATEMENT", 10, 2000);
        BigDecimal claimAmount = positiveMoney(request.claimAmount());

        ServiceOrder order = orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));

        if (!ORDER_COMPLETED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ORDER_DISPUTE_STATE|Chi co the khieu nai don COMPLETED.");
        }
        // Cua so khieu nai 2h (CONTEXT §2): chi cho khieu nai trong thoi gian escrow con giu 70%.
        // Bo qua khi holdMinutes<=0 (tat escrow) hoac completedAt null (du lieu cu khong co moc thoi gian).
        OffsetDateTime completedAt = order.getCompletedAt();
        if (escrowHoldMinutes > 0 && completedAt != null
                && OffsetDateTime.now(ZoneOffset.UTC).isAfter(completedAt.plusMinutes(escrowHoldMinutes))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DISPUTE_WINDOW_CLOSED|Đã quá thời hạn khiếu nại. Bạn chỉ có thể khiếu nại trong "
                            + formatWindow(escrowHoldMinutes) + " sau khi đơn hoàn thành.");
        }
        if (order.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ORDER_DRIVER_REQUIRED|Don hang chua co tai xe de khieu nai.");
        }
        if (claimAmount.compareTo(money(order.getTotalQuote())) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "CLAIM_AMOUNT_EXCEEDS_ORDER_TOTAL|So tien khieu nai vuot tong gia tri don.");
        }
        if (disputeRepository.existsByOrderIdAndStatusIn(orderId, OPEN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DISPUTE_ALREADY_OPEN|Don hang da co khieu nai dang mo.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        orderStatusTransitionService.transition(
                order,
                ORDER_DISPUTED,
                customer.getId(),
                customer.getRole().name(),
                now);

        Dispute dispute = Dispute.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .driverId(order.getDriverId())
                .claimType(claimType)
                .claimAmount(claimAmount)
                .customerStatement(statement)
                .status(DisputeStatus.OPEN)
                .deadline(now.plusDays(3))
                .build();

        try {
            dispute = disputeRepository.saveAndFlush(dispute);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DISPUTE_ALREADY_OPEN|Don hang da co khieu nai dang mo.", ex);
        }

        auditService.log(
                customer.getId(),
                customer.getEmail(),
                "DISPUTE_OPENED",
                "DISPUTE",
                dispute.getId().toString(),
                serialize(Map.of(
                        "order_id", order.getId(),
                        "order_code", order.getOrderCode(),
                        "claim_type", claimType,
                        "claim_amount", claimAmount)));

        notifyDisputeOpened(dispute, order);

        return toActionResponse(dispute, order, "Da tao khieu nai don hang.");
    }

    @Transactional(readOnly = true)
    public Page<DisputeListItemResponse> list(String status, int page, int size) {
        validatePage(page, size);
        String normalizedStatus = normalizeOptionalStatus(status);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<Dispute> disputes = normalizedStatus == null
                ? disputeRepository.findAll(pageable)
                : disputeRepository.findByStatus(normalizedStatus, pageable);

        Map<UUID, ServiceOrder> orders = loadOrders(disputes.getContent().stream()
                .map(Dispute::getOrderId)
                .toList());
        Map<UUID, User> users = loadUsers(disputes.getContent().stream()
                .flatMap(dispute -> List.of(dispute.getCustomerId(), dispute.getDriverId()).stream())
                .toList());

        return disputes.map(dispute -> toListItem(
                dispute,
                orders.get(dispute.getOrderId()),
                users.get(dispute.getCustomerId()),
                users.get(dispute.getDriverId())));
    }

    @Transactional(readOnly = true)
    public DisputeDetailResponse detail(UUID disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_NOT_FOUND|Khong tim thay khieu nai."));
        ServiceOrder order = orderRepository.findByIdAndDeletedAtIsNull(dispute.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));
        Map<UUID, User> users = loadUsers(List.of(dispute.getCustomerId(), dispute.getDriverId()));
        List<String> photoUrls = disputePhotoService.signedUrls(dispute.getId());
        return toDetail(dispute, order, users.get(dispute.getCustomerId()),
                users.get(dispute.getDriverId()), photoUrls);
    }

    @Transactional
    public DisputeActionResponse resolve(UUID disputeId, User actor, ResolveDisputeRequest request) {
        requireUser(actor);
        String note = normalizeText(request.note(), "INVALID_RESOLUTION_NOTE", 10, 1000);
        BigDecimal refundAmount = positiveMoney(request.refundAmount());

        Dispute dispute = findOpenForUpdate(disputeId);
        ServiceOrder order = findDisputedOrderForUpdate(dispute.getOrderId());

        if (refundAmount.compareTo(money(order.getTotalQuote())) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "AMOUNT_EXCEEDS_ORDER_TOTAL|So tien hoan vuot tong gia tri don.");
        }

        customerRefundService.refundForDispute(
                dispute.getCustomerId(),
                order.getId(),
                dispute.getId(),
                refundAmount,
                "Hoan tien khieu nai don " + order.getOrderCode());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dispute.setStatus(DisputeStatus.RESOLVED_REFUND);
        dispute.setResolutionAmount(refundAmount);
        dispute.setResolutionNote(note);
        dispute.setResolvedBy(actor.getId());
        dispute.setResolvedAt(now);
        dispute = disputeRepository.saveAndFlush(dispute);

        // Escrow: khieu nai da dong → giai phong 70% cho tai xe (neu chua release)
        releaseDriverEarningIfNeeded(order);
        // Khieu nai xu ly xong → don ve COMPLETED (IN_DISPUTE → COMPLETED)
        returnOrderToCompleted(order);

        auditService.log(
                actor.getId(),
                actor.getEmail(),
                "DISPUTE_RESOLVED",
                "DISPUTE",
                dispute.getId().toString(),
                serialize(Map.of(
                        "order_id", order.getId(),
                        "order_code", order.getOrderCode(),
                        "refund_amount", refundAmount,
                        "status", dispute.getStatus())));

        notifyDecision(dispute, order, NotificationType.DISPUTE_RESOLVED,
                "Khieu nai da duoc xu ly",
                "Khieu nai don " + order.getOrderCode() + " da duoc xu ly, hoan tien "
                        + refundAmount.toPlainString() + " VND.");

        return toActionResponse(dispute, order, "Da xu ly khieu nai va hoan tien khach hang.");
    }

    @Transactional
    public DisputeActionResponse reject(UUID disputeId, User actor, RejectDisputeRequest request) {
        requireUser(actor);
        String note = normalizeText(request.note(), "INVALID_RESOLUTION_NOTE", 10, 1000);

        Dispute dispute = findOpenForUpdate(disputeId);
        ServiceOrder order = findDisputedOrderForUpdate(dispute.getOrderId());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dispute.setStatus(DisputeStatus.CLOSED_NO_FAULT);
        dispute.setResolutionNote(note);
        dispute.setResolvedBy(actor.getId());
        dispute.setResolvedAt(now);
        dispute = disputeRepository.saveAndFlush(dispute);

        // Escrow: khieu nai bi tu choi (tai xe khong co loi) → giai phong 70% cho tai xe
        releaseDriverEarningIfNeeded(order);
        // Khieu nai dong → don ve COMPLETED (IN_DISPUTE → COMPLETED)
        returnOrderToCompleted(order);

        auditService.log(
                actor.getId(),
                actor.getEmail(),
                "DISPUTE_REJECTED",
                "DISPUTE",
                dispute.getId().toString(),
                serialize(Map.of(
                        "order_id", order.getId(),
                        "order_code", order.getOrderCode(),
                        "status", dispute.getStatus())));

        notifyDecision(dispute, order, NotificationType.DISPUTE_REJECTED,
                "Khieu nai khong duoc chap nhan",
                "Khieu nai don " + order.getOrderCode() + " da dong: " + note);

        return toActionResponse(dispute, order, "Da tu choi khieu nai.");
    }

    /**
     * Nhanh khau tru tai xe (leader chot 2026-07-10, khop HR-18 vi-truoc):
     * 1. Release earning 70% neu chua (de vi co tien ma tru).
     * 2. Tru VI tai xe ngay phan co the, hoan phan do cho khach, thong bao tai xe.
     * 3. Du → RESOLVED_DEDUCT. Thieu → luu shortfall + deadline 2 phut, thong bao
     *    tai xe nop bo sung; qua han job se khoa tai khoan + tru COC (enforcePenalty).
     * HR-18: vi khong bao gio am (chi tru min(balance, X)). AC-13: moi lan tru vi/coc
     * deu INSERT transaction cung DB transaction.
     */
    @Transactional
    public DisputeActionResponse resolveDeduct(UUID disputeId, User actor, ResolveDeductRequest request) {
        requireUser(actor);
        String note = normalizeText(request.note(), "INVALID_RESOLUTION_NOTE", 10, 1000);
        BigDecimal deductAmount = positiveMoney(request.deductAmount());

        Dispute dispute = findOpenForUpdate(disputeId);
        ServiceOrder order = findDisputedOrderForUpdate(dispute.getOrderId());

        if (dispute.getPendingDeductShortfall() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DEDUCTION_ALREADY_PENDING|Đã có khoản khấu trừ đang chờ tài xế nộp bổ sung.");
        }
        if (deductAmount.compareTo(money(order.getTotalQuote())) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "AMOUNT_EXCEEDS_ORDER_TOTAL|So tien khau tru vuot tong gia tri don.");
        }

        // Giai phong thu nhap truoc de vi tai xe co tien khau tru
        releaseDriverEarningIfNeeded(order);

        BigDecimal walletDeducted = deductFromDriverWallet(dispute, order, deductAmount, " (trừ ví)");
        if (walletDeducted.signum() > 0) {
            customerRefundService.refundForDispute(
                    dispute.getCustomerId(), order.getId(), dispute.getId(),
                    walletDeducted, "Bồi thường khiếu nại đơn " + order.getOrderCode());
            safeNotify(dispute.getDriverId(), NotificationType.PENALTY_WALLET_DEDUCTED,
                    "Đã khấu trừ tiền bồi thường từ ví",
                    "Đã trừ " + walletDeducted.toPlainString() + " VND từ ví của bạn để bồi thường khiếu nại đơn "
                            + order.getOrderCode() + ".");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dispute.setResolutionNote(note);
        dispute.setResolutionAmount(walletDeducted);
        dispute.setResolvedBy(actor.getId());

        BigDecimal shortfall = deductAmount.subtract(walletDeducted).setScale(0);
        if (shortfall.signum() <= 0) {
            // Vi du tien — hoan tat luon
            dispute.setStatus(DisputeStatus.RESOLVED_DEDUCT);
            dispute.setResolvedAt(now);
            dispute = disputeRepository.saveAndFlush(dispute);
            // Khieu nai xu ly xong → don ve COMPLETED
            returnOrderToCompleted(order);

            auditService.log(actor.getId(), actor.getEmail(), "DISPUTE_RESOLVED_DEDUCT", "DISPUTE",
                    dispute.getId().toString(),
                    serialize(Map.of("order_code", order.getOrderCode(),
                            "deduct_amount", deductAmount, "wallet_deducted", walletDeducted)));
            notifyDecision(dispute, order, NotificationType.DISPUTE_RESOLVED,
                    "Khieu nai da duoc xu ly",
                    "Khiếu nại đơn " + order.getOrderCode() + " đã xử lý: tài xế bồi thường "
                            + walletDeducted.toPlainString() + " VND.");
            return toActionResponse(dispute, order, "Đã khấu trừ ví tài xế và hoàn tiền khách hàng.");
        }

        // Vi khong du — cho tai xe nop bo sung trong 5 phut, qua han job khoa + tru coc
        dispute.setStatus(DisputeStatus.INVESTIGATING);
        dispute.setPendingDeductShortfall(shortfall);
        dispute.setDeductDeadline(now.plus(PENALTY_TOP_UP_WINDOW));
        dispute = disputeRepository.saveAndFlush(dispute);

        auditService.log(actor.getId(), actor.getEmail(), "DISPUTE_DEDUCT_PENDING", "DISPUTE",
                dispute.getId().toString(),
                serialize(Map.of("order_code", order.getOrderCode(), "deduct_amount", deductAmount,
                        "wallet_deducted", walletDeducted, "shortfall", shortfall,
                        "deadline", dispute.getDeductDeadline())));
        safeNotify(dispute.getDriverId(), NotificationType.PENALTY_TOP_UP_REQUIRED,
                "Cần nộp bổ sung tiền bồi thường",
                "Ví của bạn không đủ để bồi thường khiếu nại đơn " + order.getOrderCode()
                        + ". Còn thiếu " + shortfall.toPlainString()
                        + " VND — vui lòng nộp bổ sung trong 5 phút, nếu không tài khoản sẽ bị khóa và tiền cọc sẽ bị trừ.");

        return toActionResponse(dispute, order,
                "Đã trừ ví " + walletDeducted.toPlainString() + " VND. Còn thiếu "
                        + shortfall.toPlainString() + " VND — chờ tài xế nộp bổ sung trong 5 phút.");
    }

    /** Khoan phat dang cho cua tai xe (banner countdown FE). Tra null neu khong co. */
    @Transactional(readOnly = true)
    public DriverPenaltyResponse getPendingPenalty(UUID driverId) {
        return disputeRepository
                .findFirstByDriverIdAndPendingDeductShortfallIsNotNullOrderByDeductDeadlineAsc(driverId)
                .map(dispute -> new DriverPenaltyResponse(
                        dispute.getId(),
                        orderRepository.findById(dispute.getOrderId())
                                .map(ServiceOrder::getOrderCode).orElse(null),
                        dispute.getPendingDeductShortfall(),
                        dispute.getDeductDeadline()))
                .orElse(null);
    }

    /**
     * Tai xe nop bo sung tien phat (GIA LAP cho demo — khong qua VNPay that).
     * Chong lam dung: chi hoat dong khi CHINH tai xe do co khoan pending con han,
     * so tien cong vao vi = dung shortfall, ghi transaction day du (AC-13).
     */
    @Transactional
    public DisputeActionResponse payPenaltyMock(User driver, UUID disputeId) {
        requireUser(driver);
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_NOT_FOUND|Khong tim thay khieu nai."));
        if (!driver.getId().equals(dispute.getDriverId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN|Bạn không có quyền nộp cho khoản phạt này.");
        }
        if (dispute.getPendingDeductShortfall() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NO_PENDING_PENALTY|Không có khoản phạt nào đang chờ nộp.");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (dispute.getDeductDeadline() != null && now.isAfter(dispute.getDeductDeadline())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PENALTY_EXPIRED|Đã quá hạn nộp bổ sung. Hệ thống sẽ trừ vào tiền cọc.");
        }

        ServiceOrder order = orderRepository.findByIdForUpdate(dispute.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));
        BigDecimal shortfall = money(dispute.getPendingDeductShortfall());

        // Nap gia lap vao vi dung bang shortfall (demo MOCK MODE theo CLAUDE.md)
        creditDriverWallet(dispute, order, shortfall,
                "Nộp bổ sung tiền bồi thường đơn " + order.getOrderCode() + " (giả lập demo)");
        // Tru lai ngay va hoan cho khach
        BigDecimal deducted = deductFromDriverWallet(dispute, order, shortfall, " (nộp bổ sung)");
        customerRefundService.refundForDispute(
                dispute.getCustomerId(), order.getId(), dispute.getId(),
                deducted, "Bồi thường khiếu nại đơn " + order.getOrderCode());

        dispute.setResolutionAmount(moneyOrZero(dispute.getResolutionAmount()).add(deducted));
        dispute.setPendingDeductShortfall(null);
        dispute.setDeductDeadline(null);
        dispute.setStatus(DisputeStatus.RESOLVED_DEDUCT);
        dispute.setResolvedAt(now);
        dispute = disputeRepository.saveAndFlush(dispute);
        // Nop du → khieu nai dong → don ve COMPLETED
        returnOrderToCompleted(order);

        auditService.log(driver.getId(), driver.getEmail(), "DISPUTE_PENALTY_PAID", "DISPUTE",
                dispute.getId().toString(),
                serialize(Map.of("order_code", order.getOrderCode(), "amount", deducted)));
        safeNotify(dispute.getDriverId(), NotificationType.PENALTY_SETTLED,
                "Đã nộp đủ tiền bồi thường",
                "Bạn đã nộp bổ sung " + deducted.toPlainString() + " VND cho khiếu nại đơn "
                        + order.getOrderCode() + ". Tài khoản hoạt động bình thường.");
        safeNotify(dispute.getCustomerId(), NotificationType.DISPUTE_RESOLVED,
                "Khieu nai da duoc xu ly",
                "Khiếu nại đơn " + order.getOrderCode() + " đã xử lý xong, bạn được hoàn tổng cộng "
                        + moneyOrZero(dispute.getResolutionAmount()).toPlainString() + " VND.");

        return toActionResponse(dispute, order, "Đã nộp bổ sung thành công. Khiếu nại được đóng.");
    }

    /**
     * Job goi khi qua han nop bo sung: thu vi lan cuoi → tru COC phan con thieu →
     * KHOA tai khoan (SUSPENDED) kem ly do + so tien can nap lai cho du coc 3 trieu.
     * Khach duoc hoan toi da phan thu duoc tu tai xe (leader chot: cong ty khong bu).
     */
    @Transactional
    public void enforcePenalty(UUID disputeId) {
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId).orElse(null);
        if (dispute == null || dispute.getPendingDeductShortfall() == null) {
            return; // da duoc xu ly (tai xe nop kip) — idempotent
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (dispute.getDeductDeadline() != null && now.isBefore(dispute.getDeductDeadline())) {
            return; // chua den han
        }

        ServiceOrder order = orderRepository.findByIdForUpdate(dispute.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("enforcePenalty: khong tim thay don cua dispute {}", disputeId);
            return;
        }

        BigDecimal shortfall = money(dispute.getPendingDeductShortfall());

        // Thu vi lan cuoi (phong khi co earning vua ve)
        BigDecimal walletPart = deductFromDriverWallet(dispute, order, shortfall, " (trừ ví, quá hạn)");
        BigDecimal remaining = shortfall.subtract(walletPart).setScale(0);

        BigDecimal depositPart = BigDecimal.ZERO.setScale(0);
        if (remaining.signum() > 0) {
            depositPart = deductFromDriverDeposit(dispute, order, remaining);
            BigDecimal uncovered = remaining.subtract(depositPart).setScale(0);
            suspendDriverForPenalty(dispute.getDriverId(), order.getOrderCode(), uncovered);
        }

        BigDecimal collected = walletPart.add(depositPart).setScale(0);
        if (collected.signum() > 0) {
            customerRefundService.refundForDispute(
                    dispute.getCustomerId(), order.getId(), dispute.getId(),
                    collected, "Bồi thường khiếu nại đơn " + order.getOrderCode() + " (từ tiền cọc tài xế)");
        }

        dispute.setResolutionAmount(moneyOrZero(dispute.getResolutionAmount()).add(collected));
        dispute.setResolutionNote((dispute.getResolutionNote() != null ? dispute.getResolutionNote() : "")
                + " | Quá hạn nộp bổ sung: trừ ví " + walletPart.toPlainString()
                + " VND, trừ cọc " + depositPart.toPlainString() + " VND, khóa tài khoản tài xế.");
        dispute.setPendingDeductShortfall(null);
        dispute.setDeductDeadline(null);
        dispute.setStatus(DisputeStatus.RESOLVED_DEDUCT);
        dispute.setResolvedAt(now);
        disputeRepository.saveAndFlush(dispute);
        // Da thu xong (vi + coc) → khieu nai dong → don ve COMPLETED
        returnOrderToCompleted(order);

        auditService.log(null, "SYSTEM", "DISPUTE_PENALTY_ENFORCED", "DISPUTE",
                dispute.getId().toString(),
                serialize(Map.of("order_code", order.getOrderCode(), "wallet_part", walletPart,
                        "deposit_part", depositPart, "shortfall", shortfall)));
        safeNotify(dispute.getCustomerId(), NotificationType.DISPUTE_RESOLVED,
                "Khieu nai da duoc xu ly",
                "Khiếu nại đơn " + order.getOrderCode() + " đã xử lý xong, bạn được hoàn tổng cộng "
                        + moneyOrZero(dispute.getResolutionAmount()).toPlainString() + " VND.");
    }

    /** Danh sach dispute qua han nop bo sung — cho scheduled job. */
    @Transactional(readOnly = true)
    public List<UUID> findExpiredPenaltyIds() {
        return disputeRepository.findExpiredPendingDeductionIds(OffsetDateTime.now(ZoneOffset.UTC));
    }

    // Tru vi tai xe toi da min(balance, maxAmount) — HR-18 khong am; AC-13 ghi transaction kem balance_after
    private BigDecimal deductFromDriverWallet(Dispute dispute, ServiceOrder order,
                                              BigDecimal maxAmount, String descSuffix) {
        UUID driverId = dispute.getDriverId();
        driverWalletRepository.insertIfMissing(driverId);
        DriverWallet wallet = driverWalletRepository.findByDriverIdForUpdate(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "DRIVER_WALLET_NOT_FOUND|Khong tim thay vi tai xe."));

        BigDecimal balance = moneyOrZero(wallet.getBalance());
        BigDecimal part = balance.min(money(maxAmount));
        if (part.signum() <= 0) {
            return BigDecimal.ZERO.setScale(0);
        }
        BigDecimal after = balance.subtract(part).setScale(0);
        wallet.setBalance(after);
        driverWalletRepository.saveAndFlush(wallet);

        transactionRepository.saveAndFlush(Transaction.builder()
                .userId(driverId)
                .type(TransactionType.DAMAGE_DEDUCTION)
                .amount(part.negate())
                .relatedOrderId(order.getId())
                .relatedDisputeId(dispute.getId())
                .balanceAfter(after)
                .description("Khấu trừ bồi thường khiếu nại đơn " + order.getOrderCode() + descSuffix)
                .build());
        return part;
    }

    // Cong tien vao vi tai xe (nop bo sung gia lap) — ghi transaction WALLET_TOP_UP
    private void creditDriverWallet(Dispute dispute, ServiceOrder order, BigDecimal amount, String description) {
        UUID driverId = dispute.getDriverId();
        driverWalletRepository.insertIfMissing(driverId);
        DriverWallet wallet = driverWalletRepository.findByDriverIdForUpdate(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "DRIVER_WALLET_NOT_FOUND|Khong tim thay vi tai xe."));
        BigDecimal after = moneyOrZero(wallet.getBalance()).add(money(amount)).setScale(0);
        wallet.setBalance(after);
        driverWalletRepository.saveAndFlush(wallet);

        transactionRepository.saveAndFlush(Transaction.builder()
                .userId(driverId)
                .type(TransactionType.WALLET_TOP_UP)
                .amount(money(amount))
                .relatedOrderId(order.getId())
                .relatedDisputeId(dispute.getId())
                .balanceAfter(after)
                .description(description)
                .build());
    }

    // Tru coc tai xe toi da min(deposit_amount, maxAmount) — khong de coc am
    private BigDecimal deductFromDriverDeposit(Dispute dispute, ServiceOrder order, BigDecimal maxAmount) {
        DriverProfile profile = driverProfileRepository.findByUserId(dispute.getDriverId()).orElse(null);
        if (profile == null) {
            log.warn("enforcePenalty: khong tim thay driver_profile cua tai xe {}", dispute.getDriverId());
            return BigDecimal.ZERO.setScale(0);
        }
        BigDecimal deposit = moneyOrZero(profile.getDepositAmount());
        BigDecimal part = deposit.min(money(maxAmount));
        if (part.signum() <= 0) {
            return BigDecimal.ZERO.setScale(0);
        }
        profile.setDepositAmount(deposit.subtract(part).setScale(0));
        driverProfileRepository.saveAndFlush(profile);

        transactionRepository.saveAndFlush(Transaction.builder()
                .userId(dispute.getDriverId())
                .type(TransactionType.DAMAGE_DEDUCTION)
                .amount(part.negate())
                .relatedOrderId(order.getId())
                .relatedDisputeId(dispute.getId())
                .description("Khấu trừ bồi thường khiếu nại đơn " + order.getOrderCode() + " (trừ tiền cọc)")
                .build());
        return part;
    }

    // Khoa tai khoan tai xe vi thieu tien phat — ly do luu kem SO TIEN can nap lai cho du coc
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

        String reason = "Thiếu tiền đóng phạt bồi thường khiếu nại đơn " + orderCode + ". "
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
                "Tài khoản bị khóa do thiếu tiền đóng phạt", reason);
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(0) : value.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Dua don ve COMPLETED sau khi khieu nai da xu ly xong (CONTEXT state machine: IN_DISPUTE → COMPLETED).
     * Set truc tiep (khong qua orderStatusTransitionService) de KHONG ban lai OrderStatusChangedEvent →
     * tranh gui notification/email "Don da hoan thanh" trung lap; ket qua khieu nai da thong bao rieng
     * qua notifyDecision. Thay doi state da duoc audit trong log DISPUTE_* kem order_code (HR-13).
     */
    private void returnOrderToCompleted(ServiceOrder order) {
        if (!ORDER_COMPLETED.equals(order.getStatus())) {
            order.setStatus(ORDER_COMPLETED);
            orderRepository.save(order);
        }
    }

    // Hien thi cua so khieu nai tieng Viet: tron gio thi "X gio", con lai "X phut"
    private String formatWindow(long minutes) {
        if (minutes % 60 == 0) {
            return (minutes / 60) + " giờ";
        }
        return minutes + " phút";
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    private Dispute findOpenForUpdate(UUID disputeId) {
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_NOT_FOUND|Khong tim thay khieu nai."));
        if (!OPEN_STATUSES.contains(dispute.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DISPUTE_ALREADY_RESOLVED|Khieu nai da duoc xu ly.");
        }
        return dispute;
    }

    private ServiceOrder findDisputedOrderForUpdate(UUID orderId) {
        ServiceOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));
        if (!DISPUTED_ORDER_STATUSES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ORDER_DISPUTE_STATE|Trang thai don khong hop le.");
        }
        return order;
    }

    /**
     * Giai phong thu nhap escrow cho tai xe khi khieu nai da dong (resolve/reject).
     * Idempotent: bo qua neu da release (earning_released_at) — tranh cong tien 2 lan.
     * Giu hanh vi cu (tai xe nhan du 70%); hoan tien khach neu co lay tu quy cong ty (refund service).
     */
    private void releaseDriverEarningIfNeeded(ServiceOrder order) {
        if (order.getDriverId() == null || order.getEarningReleasedAt() != null) {
            return;
        }
        driverEarningService.creditEarning(order);
        order.setEarningReleasedAt(OffsetDateTime.now(ZoneOffset.UTC));
        orderRepository.save(order);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|So trang khong hop le.");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Kich thuoc trang phai tu 1 den 100.");
        }
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!Set.of(
                DisputeStatus.OPEN,
                DisputeStatus.INVESTIGATING,
                DisputeStatus.RESOLVED_REFUND,
                DisputeStatus.RESOLVED_DEDUCT,
                DisputeStatus.CLOSED_NO_FAULT).contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_DISPUTE_FILTER|Bo loc khieu nai khong hop le.");
        }
        return normalized;
    }

    private String normalizeClaimType(String claimType) {
        String normalized = claimType == null ? "" : claimType.trim().toUpperCase();
        if (!VALID_CLAIM_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_CLAIM_TYPE|Loai khieu nai khong hop le.");
        }
        return normalized;
    }

    private String normalizeText(String value, String errorCode, int minLength, int maxLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        boolean hasLetter = normalized.codePoints().anyMatch(Character::isLetter);
        if (normalized.length() < minLength || normalized.length() > maxLength || !hasLetter) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    errorCode + "|Noi dung khong hop le.");
        }
        return normalized;
    }

    private BigDecimal positiveMoney(BigDecimal value) {
        BigDecimal amount = money(value);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RESOLUTION_AMOUNT|So tien xu ly khong hop le.");
        }
        return amount;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RESOLUTION_AMOUNT|So tien xu ly khong hop le.");
        }
        try {
            return value.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RESOLUTION_AMOUNT|So tien xu ly phai la VND nguyen dong.");
        }
    }

    private void requireUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui long dang nhap de tiep tuc.");
        }
    }

    private Map<UUID, ServiceOrder> loadOrders(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findAllById(orderIds)
                .stream()
                .distinct()
                .collect(Collectors.toMap(ServiceOrder::getId, Function.identity()));
    }

    private Map<UUID, User> loadUsers(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds)
                .stream()
                .distinct()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private DisputeListItemResponse toListItem(
            Dispute dispute,
            ServiceOrder order,
            User customer,
            User driver
    ) {
        return new DisputeListItemResponse(
                dispute.getId(),
                dispute.getOrderId(),
                order != null ? order.getOrderCode() : null,
                order != null ? order.getStatus() : null,
                dispute.getCustomerId(),
                customer != null ? customer.getFullName() : null,
                dispute.getDriverId(),
                driver != null ? driver.getFullName() : null,
                dispute.getClaimType(),
                dispute.getClaimAmount(),
                dispute.getStatus(),
                dispute.getCreatedAt(),
                dispute.getDeadline());
    }

    private DisputeDetailResponse toDetail(
            Dispute dispute,
            ServiceOrder order,
            User customer,
            User driver,
            List<String> photoUrls
    ) {
        return new DisputeDetailResponse(
                dispute.getId(),
                dispute.getStatus(),
                dispute.getClaimType(),
                dispute.getClaimAmount(),
                dispute.getCustomerStatement(),
                dispute.getDriverResponse(),
                dispute.getDriverResponseAt(),
                dispute.getResolutionAmount(),
                dispute.getResolutionNote(),
                dispute.getResolvedBy(),
                dispute.getResolvedAt(),
                dispute.getCreatedAt(),
                dispute.getDeadline(),
                dispute.getPendingDeductShortfall(),
                dispute.getDeductDeadline(),
                photoUrls,
                new DisputeDetailResponse.OrderSummary(
                        order.getId(),
                        order.getOrderCode(),
                        order.getStatus(),
                        order.getTotalQuote(),
                        order.getScheduledAt(),
                        order.getCompletedAt(),
                        order.getPickupAddress(),
                        order.getDropoffAddress(),
                        order.getVehicleType()),
                new DisputeDetailResponse.PartySummary(
                        dispute.getCustomerId(),
                        customer != null ? customer.getFullName() : null,
                        customer != null ? customer.getPhone() : null),
                new DisputeDetailResponse.PartySummary(
                        dispute.getDriverId(),
                        driver != null ? driver.getFullName() : null,
                        driver != null ? driver.getPhone() : null));
    }

    private DisputeActionResponse toActionResponse(Dispute dispute, ServiceOrder order, String message) {
        return new DisputeActionResponse(
                dispute.getId(),
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                dispute.getStatus(),
                dispute.getResolutionAmount(),
                dispute.getResolutionNote(),
                dispute.getResolvedBy(),
                dispute.getResolvedAt(),
                message);
    }

    private void notifyDisputeOpened(Dispute dispute, ServiceOrder order) {
        String title = "Khieu nai moi";
        String message = "Don " + order.getOrderCode() + " vua co khieu nai moi.";
        safeNotify(dispute.getCustomerId(), NotificationType.DISPUTE_OPENED, title, message);
        safeNotify(dispute.getDriverId(), NotificationType.DISPUTE_OPENED, title, message);
        notifyOperations(NotificationType.DISPUTE_OPENED, title, message);
    }

    private void notifyDecision(Dispute dispute, ServiceOrder order, String type, String title, String message) {
        safeNotify(dispute.getCustomerId(), type, title, message);
        safeNotify(dispute.getDriverId(), type, title, message);
    }

    private void notifyOperations(String type, String title, String message) {
        userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE)
                .forEach(user -> safeNotify(user.getId(), type, title, message));
        userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE)
                .forEach(user -> safeNotify(user.getId(), type, title, message));
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

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
        } catch (JsonProcessingException ex) {
            return payload.toString();
        }
    }
}
