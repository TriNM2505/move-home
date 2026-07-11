package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.customer.finance.CreateCustomerWithdrawalRequest;
import vn.movehome.backend.customer.finance.CustomerWithdrawalItemResponse;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequest;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequestRepository;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequestResponse;
import vn.movehome.backend.dto.customer.wallet.TransactionDTO;
import vn.movehome.backend.dto.customer.wallet.WalletSummaryDTO;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.entity.WalletTransaction;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;
import vn.movehome.backend.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String PENDING_STATUS = "PENDING";

    // Whitelist ngan hang ho tro: code -> ten hien thi (snapshot vao customer_withdrawal_request).
    // Giu dong bo voi DriverEarningService.SUPPORTED_BANKS.
    private static final Map<String, String> SUPPORTED_BANKS = Map.of(
            "VCB", "Vietcombank",
            "BIDV", "BIDV",
            "CTG", "VietinBank",
            "TCB", "Techcombank",
            "MB", "MB Bank",
            "ACB", "ACB",
            "VPB", "VPBank",
            "AGR", "Agribank");

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final CustomerWithdrawalRequestRepository customerWithdrawalRequestRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public WalletSummaryDTO getOrCreateSummary(UUID customerId) {
        CustomerWallet wallet = walletRepository.findByCustomerId(customerId)
                .orElseGet(() -> walletRepository.save(CustomerWallet.builder()
                        .customerId(customerId)
                        .balance(BigDecimal.ZERO)
                        .totalToppedUp(BigDecimal.ZERO)
                        .totalSpent(BigDecimal.ZERO)
                        .totalWithdrawn(BigDecimal.ZERO)
                        .build()));

        return new WalletSummaryDTO(
                money(wallet.getBalance()),
                money(wallet.getTotalToppedUp()),
                money(wallet.getTotalSpent()),
                money(wallet.getTotalWithdrawn())
        );
    }

    @Transactional(readOnly = true)
    public Page<TransactionDTO> getTransactions(UUID customerId, int page, int size) {
        validatePage(page, size);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return transactionRepository.findByUserId(customerId, pageable)
                .map(this::toTransactionDTO);
    }

    /**
     * Khach hang tao yeu cau rut tien tu vi. Yeu cau PENDING cho Admin duyet thu cong.
     * HR-18: khong cho rut vuot so du kha dung (balance - tong PENDING). Vi chi bi tru khi PROCESSED.
     * AC-08: tien BigDecimal scale=0. HR-13: ghi audit qua log; notification cho Admin.
     */
    @Transactional
    public CustomerWithdrawalRequestResponse createWithdrawal(User customer, CreateCustomerWithdrawalRequest request) {
        UUID customerId = customer.getId();
        BigDecimal amount = validateWithdrawalAmount(request != null ? request.amount() : null);

        walletRepository.insertIfMissing(customerId);
        CustomerWallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "CUSTOMER_WALLET_NOT_FOUND|Không tìm thấy ví khách hàng."));

        // So tien dang giu cho boi cac yeu cau PENDING (khoa de tinh chinh xac khi co nhieu request).
        BigDecimal pendingTotal = customerWithdrawalRequestRepository.findPendingByCustomerIdForUpdate(customerId)
                .stream()
                .map(CustomerWithdrawalRequest::getAmount)
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

        // Thong tin ngan hang nhan tien — bat buoc de Admin biet chuyen khoan di dau.
        String bankCode = validateBankCode(request.bankCode());
        String bankAccountNumber = validateBankAccountNumber(request.bankAccountNumber());

        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .customerId(customerId)
                .amount(amount)
                .bankCode(bankCode)
                .bankNameSnapshot(SUPPORTED_BANKS.get(bankCode))
                .bankAccountNumber(bankAccountNumber)
                .bankAccountHolder(normalizeAccountHolder(customer.getFullName()))
                .status(PENDING_STATUS)
                .idempotencyKey(UUID.randomUUID())
                .build();

        CustomerWithdrawalRequest saved = customerWithdrawalRequestRepository.saveAndFlush(withdrawal);
        OffsetDateTime requestedAt = saved.getRequestedAt() != null
                ? saved.getRequestedAt()
                : OffsetDateTime.now(ZoneOffset.UTC);

        log.info(
                "customer_withdrawal_state_audit actor_id={} actor_role=CUSTOMER timestamp={} from_state=null to_state={} entity_id={}",
                customerId, requestedAt, PENDING_STATUS, saved.getId());

        notifyAdminsWithdrawalRequested(customer, amount);

        return new CustomerWithdrawalRequestResponse(
                saved.getId(),
                money(saved.getAmount()),
                saved.getStatus(),
                "Yêu cầu rút tiền đã được gửi.",
                requestedAt
        );
    }

    /**
     * Lich su yeu cau rut tien cua khach hang — moi nhat truoc.
     * FE customer/withdrawal-history.html doc Page.content.
     */
    @Transactional(readOnly = true)
    public Page<CustomerWithdrawalItemResponse> getWithdrawals(UUID customerId, int page, int size) {
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "requestedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        return customerWithdrawalRequestRepository.findByCustomerId(customerId, pageable)
                .map(withdrawal -> new CustomerWithdrawalItemResponse(
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

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    // Bao cho tat ca Admin ACTIVE co yeu cau rut tien moi — loi notification khong lam fail viec tao yeu cau.
    private void notifyAdminsWithdrawalRequested(User customer, BigDecimal amount) {
        try {
            List<User> admins = userRepository.findByRoleAndStatusAndDeletedAtIsNull(
                    UserRole.ADMIN, UserStatus.ACTIVE);
            String customerName = customer.getFullName() != null ? customer.getFullName() : "Khách hàng";
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<Notification> notifications = admins.stream()
                    .map(admin -> Notification.builder()
                            .userId(admin.getId())
                            .type(NotificationType.WITHDRAWAL_REQUESTED)
                            .title("Yêu cầu rút tiền mới (khách hàng)")
                            .message("Khách hàng " + customerName + " yêu cầu rút "
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

    private TransactionDTO toTransactionDTO(WalletTransaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                null,
                transaction.getRelatedOrderId(),
                transaction.getDescription(),
                maskVnpayTxnRef(transaction.getVnpayTxnRef()),
                transaction.getCreatedAt()
        );
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
        if (normalized.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Số tiền rút phải lớn hơn 0.");
        }
        return normalized;
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

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(0);
        }
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    // Che so tai khoan khi tra ve FE — chi hien 4 so cuoi (giong DriverEarningService/AdminWithdrawalService).
    private String maskAccount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        String last4 = trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4);
        return "******" + last4;
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

    private String maskVnpayTxnRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
