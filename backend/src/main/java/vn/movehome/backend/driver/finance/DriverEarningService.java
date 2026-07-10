package vn.movehome.backend.driver.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.NotificationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverEarningService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String PENDING_STATUS = "PENDING";
    // Ghi thu nhap hop le cho don da giao xong: COMPLETED (release escrow sau 2h) hoac
    // DISPUTED (release khi Manager dong khieu nai). Chuyen giao da hoan tat, chi khac ve dong tien.
    private static final Set<String> EARNING_ELIGIBLE_STATUSES = Set.of(COMPLETED_STATUS, "DISPUTED");
    private static final BigDecimal MIN_WITHDRAWAL_AMOUNT = new BigDecimal("100000");
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.3000");

    // Cac loai giao dich hien tren trang Thu nhap tai xe: thu nhap (+), khau tru boi thuong (-), nop bo sung (+)
    private static final java.util.Set<TransactionType> DRIVER_WALLET_TX_TYPES = java.util.EnumSet.of(
            TransactionType.DRIVER_EARNING,
            TransactionType.DAMAGE_DEDUCTION,
            TransactionType.WALLET_TOP_UP);

    // Whitelist ngan hang ho tro: code -> ten hien thi (snapshot vao withdrawal_request)
    private static final Map<String, String> SUPPORTED_BANKS = Map.of(
            "VCB", "Vietcombank",
            "BIDV", "BIDV",
            "CTG", "VietinBank",
            "TCB", "Techcombank",
            "MB", "MB Bank",
            "ACB", "ACB",
            "VPB", "VPBank",
            "AGR", "Agribank");

    private final DriverWalletRepository driverWalletRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public void creditEarning(ServiceOrder order) {
        validateCompletedOrder(order);

        UUID driverId = order.getDriverId();
        driverWalletRepository.insertIfMissing(driverId);
        DriverWallet wallet = findWalletForUpdate(driverId);

        if (transactionRepository.existsByTypeAndRelatedOrderId(TransactionType.DRIVER_EARNING, order.getId())) {
            log.info("driver_earning_idempotent_skip order_id={} driver_id={}", order.getId(), driverId);
            return;
        }

        BigDecimal totalQuote = money(order.getTotalQuote());
        BigDecimal commissionRate = normalizeCommissionRate(order.getCommissionRateSnapshot());
        BigDecimal earning = money(totalQuote.multiply(BigDecimal.ONE.subtract(commissionRate)));
        BigDecimal platformFee = totalQuote.subtract(earning).setScale(0);

        wallet.setBalance(money(wallet.getBalance()).add(earning));
        wallet.setTotalEarned(money(wallet.getTotalEarned()).add(earning));
        driverWalletRepository.save(wallet);

        UUID platformUserId = findPlatformUserId();
        transactionRepository.saveAll(List.of(
                Transaction.builder()
                        .userId(driverId)
                        .type(TransactionType.DRIVER_EARNING)
                        .amount(earning)
                        .relatedOrderId(order.getId())
                        .description("Thu nhập tài xế từ đơn " + order.getOrderCode())
                        .build(),
                Transaction.builder()
                        .userId(platformUserId)
                        .type(TransactionType.PLATFORM_FEE)
                        .amount(platformFee)
                        .relatedOrderId(order.getId())
                        .description("Phí nền tảng từ đơn " + order.getOrderCode())
                        .build()
        ));

        log.info(
                "driver_earning_audit actor_id={} actor_role=SYSTEM timestamp={} order_id={} earning={} platform_fee={}",
                driverId, OffsetDateTime.now(ZoneOffset.UTC), order.getId(), earning, platformFee);
    }

    @Transactional
    public DriverWalletSummaryResponse getWallet(UUID driverId) {
        driverWalletRepository.insertIfMissing(driverId);
        DriverWallet wallet = driverWalletRepository.findByDriverId(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "DRIVER_WALLET_NOT_FOUND|Không tìm thấy ví tài xế."));

        return new DriverWalletSummaryResponse(
                money(wallet.getBalance()),
                money(wallet.getTotalEarned()),
                money(wallet.getTotalWithdrawn())
        );
    }

    @Transactional(readOnly = true)
    public Page<DriverEarningResponse> getEarnings(UUID driverId, int page, int size) {
        validatePage(page, size);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );
        // Hien thi ca thu nhap va khau tru boi thuong + nop bo sung, de tai xe thay khoan bi tru (huong A)
        Page<Transaction> transactions = transactionRepository.findByUserIdAndTypeIn(
                driverId,
                DRIVER_WALLET_TX_TYPES,
                pageable
        );

        Map<UUID, String> orderCodes = loadOrderCodes(transactions.getContent());
        return transactions.map(transaction -> new DriverEarningResponse(
                transaction.getId(),
                money(transaction.getAmount()),
                transaction.getRelatedOrderId(),
                orderCodes.get(transaction.getRelatedOrderId()),
                transaction.getDescription(),
                toOffsetDateTime(transaction.getCreatedAt())
        ));
    }

    @Transactional
    public WithdrawalRequestResponse createWithdrawal(User driver, CreateWithdrawalRequest request) {
        UUID driverId = driver.getId();
        BigDecimal amount = validateWithdrawalAmount(request != null ? request.amount() : null);

        driverWalletRepository.insertIfMissing(driverId);
        DriverWallet wallet = findWalletForUpdate(driverId);
        BigDecimal pendingTotal = withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)
                .stream()
                .map(WithdrawalRequest::getAmount)
                .filter(Objects::nonNull)
                .map(this::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = money(wallet.getBalance()).subtract(pendingTotal);
        if (available.signum() < 0) {
            available = BigDecimal.ZERO;
        }

        if (amount.compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INSUFFICIENT_AVAILABLE_BALANCE|Số tiền rút vượt quá số dư khả dụng.");
        }

        // Thong tin ngan hang nhan tien — bat buoc de Admin biet chuyen khoan di dau
        String bankCode = validateBankCode(request.bankCode());
        String bankAccountNumber = validateBankAccountNumber(request.bankAccountNumber());

        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .driverId(driverId)
                .amount(amount)
                .bankCode(bankCode)
                .bankNameSnapshot(SUPPORTED_BANKS.get(bankCode))
                .bankAccountNumber(bankAccountNumber)
                .bankAccountHolder(normalizeAccountHolder(driver.getFullName()))
                .status(PENDING_STATUS)
                .idempotencyKey(UUID.randomUUID())
                .build();

        WithdrawalRequest saved = withdrawalRequestRepository.saveAndFlush(withdrawal);
        OffsetDateTime requestedAt = saved.getRequestedAt() != null
                ? saved.getRequestedAt()
                : OffsetDateTime.now(ZoneOffset.UTC);

        log.info(
                "withdrawal_state_audit actor_id={} actor_role=DRIVER timestamp={} from_state=null to_state={} entity_id={}",
                driverId, requestedAt, PENDING_STATUS, saved.getId());

        // Bao cho tat ca Admin dang ACTIVE co yeu cau rut tien moi cho duyet
        notifyAdminsWithdrawalRequested(driver, amount);

        return new WithdrawalRequestResponse(
                saved.getId(),
                money(saved.getAmount()),
                saved.getStatus(),
                "Yêu cầu rút tiền đã được gửi.",
                requestedAt
        );
    }

    /**
     * Lich su yeu cau rut tien cua tai xe — moi nhat truoc.
     * FE driver/withdrawal-history.html doc Page.content voi cac field:
     * id, amount, requestedAt, status, processedAt.
     */
    @Transactional(readOnly = true)
    public Page<DriverWithdrawalItemResponse> getWithdrawals(UUID driverId, int page, int size) {
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "requestedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        return withdrawalRequestRepository.findByDriverId(driverId, pageable)
                .map(withdrawal -> new DriverWithdrawalItemResponse(
                        withdrawal.getId(),
                        money(withdrawal.getAmount()),
                        withdrawal.getStatus(),
                        withdrawal.getBankNameSnapshot(),
                        maskAccount(withdrawal.getBankAccountNumber()),
                        withdrawal.getRejectionReason(),
                        withdrawal.getRequestedAt(),
                        withdrawal.getProcessedAt()
                ));
    }

    // Tao notification in-app cho moi Admin ACTIVE — loi notification khong lam fail viec tao yeu cau
    private void notifyAdminsWithdrawalRequested(User driver, BigDecimal amount) {
        try {
            List<User> admins = userRepository.findByRoleAndStatusAndDeletedAtIsNull(
                    UserRole.ADMIN, UserStatus.ACTIVE);
            String driverName = driver.getFullName() != null ? driver.getFullName() : "Tài xế";
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<Notification> notifications = admins.stream()
                    .map(admin -> Notification.builder()
                            .userId(admin.getId())
                            .type(NotificationType.WITHDRAWAL_REQUESTED)
                            .title("Yêu cầu rút tiền mới")
                            .message("Tài xế " + driverName + " yêu cầu rút "
                                    + money(amount).toPlainString() + " VND. Vui lòng xử lý.")
                            .isRead(false)
                            .createdAt(now)
                            .build())
                    .toList();
            notificationRepository.saveAll(notifications);
        } catch (RuntimeException ex) {
            log.warn("Khong the tao notification WITHDRAWAL_REQUESTED cho admin: {}", ex.getMessage());
        }
    }

    private String validateBankCode(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Vui lòng chọn ngân hàng nhận tiền.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_BANKS.containsKey(normalized)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Ngân hàng không được hỗ trợ.");
        }
        return normalized;
    }

    private String validateBankAccountNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Vui lòng nhập số tài khoản ngân hàng.");
        }
        String normalized = value.trim();
        if (!normalized.matches("^[0-9]{8,15}$")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Số tài khoản không hợp lệ (phải gồm 8 đến 15 chữ số).");
        }
        return normalized;
    }

    // Che so tai khoan khi tra ve FE — chi hien 4 so cuoi (giong AdminWithdrawalService)
    private String maskAccount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        String last4 = trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4);
        return "******" + last4;
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    private void validateCompletedOrder(ServiceOrder order) {
        if (order == null || order.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Đơn hàng không hợp lệ.");
        }
        if (!EARNING_ELIGIBLE_STATUSES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ORDER_STATUS|Chỉ ghi thu nhập cho đơn đã hoàn thành.");
        }
        if (order.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ORDER_DRIVER_REQUIRED|Đơn hàng chưa có tài xế nhận.");
        }
        if (order.getTotalQuote() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Đơn hàng chưa có tổng tiền hợp lệ.");
        }
    }

    private DriverWallet findWalletForUpdate(UUID driverId) {
        return driverWalletRepository.findByDriverIdForUpdate(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "DRIVER_WALLET_NOT_FOUND|Không tìm thấy ví tài xế."));
    }

    private UUID findPlatformUserId() {
        return userRepository.findFirstByRoleAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                        UserRole.ADMIN,
                        UserStatus.ACTIVE)
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "PLATFORM_ACCOUNT_NOT_FOUND|Không tìm thấy tài khoản nền tảng để ghi phí."));
    }

    private Map<UUID, String> loadOrderCodes(List<Transaction> transactions) {
        Set<UUID> orderIds = transactions.stream()
                .map(Transaction::getRelatedOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Map.of();
        }

        return orderRepository.findAllById(orderIds)
                .stream()
                .collect(Collectors.toMap(
                        ServiceOrder::getId,
                        ServiceOrder::getOrderCode,
                        (left, right) -> left
                ));
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Số trang không hợp lệ.");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.");
        }
    }

    private BigDecimal validateWithdrawalAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Vui lòng nhập số tiền cần rút.");
        }
        BigDecimal normalized;
        try {
            normalized = amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Số tiền rút phải là VND nguyên đồng.");
        }

        if (normalized.compareTo(MIN_WITHDRAWAL_AMOUNT) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Số tiền rút tối thiểu là 100.000 VND.");
        }
        return normalized;
    }

    private BigDecimal normalizeCommissionRate(BigDecimal value) {
        BigDecimal commissionRate = value != null ? value : DEFAULT_COMMISSION_RATE;
        if (commissionRate.compareTo(BigDecimal.ZERO) < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_COMMISSION_RATE|Tỷ lệ phí nền tảng của đơn hàng không hợp lệ.");
        }
        return commissionRate;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(0);
        }
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    private String normalizeAccountHolder(String value) {
        String normalized = value == null || value.isBlank() ? "CHUA CAP NHAT" : value;
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
        if (normalized.length() > 100) {
            return normalized.substring(0, 100);
        }
        return normalized;
    }
}
