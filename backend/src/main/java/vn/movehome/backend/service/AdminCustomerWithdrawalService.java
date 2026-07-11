package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequest;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequestRepository;
import vn.movehome.backend.dto.admin.finance.AdminCustomerWithdrawalItemResponse;
import vn.movehome.backend.dto.admin.finance.PendingCustomerWithdrawalPageResponse;
import vn.movehome.backend.dto.admin.finance.ProcessWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.RejectWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.WithdrawalActionResponse;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.repository.AuditLogRepository;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Admin duyet/tu choi yeu cau rut tien cua khach hang (mirror AdminWithdrawalService cua tai xe).
 * HR-18: vi khong am, chi tru khi PROCESSED va so du du. AC-13: UPDATE vi kem INSERT transaction cung TX.
 * HR-13: ghi AuditLog. AC-14: status VARCHAR + CHECK.
 */
@Service
@RequiredArgsConstructor
public class AdminCustomerWithdrawalService {

    private static final String PENDING = "PENDING";
    private static final String PROCESSED = "PROCESSED";
    private static final String REJECTED = "REJECTED";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern BANK_REF_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]+$");

    private final CustomerWithdrawalRequestRepository customerWithdrawalRequestRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public PendingCustomerWithdrawalPageResponse getPending(int page, int size) {
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "requestedAt").and(Sort.by(Sort.Direction.ASC, "id"))
        );
        Page<CustomerWithdrawalRequest> withdrawals = customerWithdrawalRequestRepository.findByStatus(PENDING, pageable);
        Set<UUID> customerIds = withdrawals.getContent().stream()
                .map(CustomerWithdrawalRequest::getCustomerId)
                .collect(Collectors.toSet());
        Map<UUID, User> customers = loadUsers(customerIds);
        Map<UUID, CustomerWallet> wallets = loadWallets(customerIds);

        List<AdminCustomerWithdrawalItemResponse> content = withdrawals.getContent()
                .stream()
                .map(withdrawal -> toPendingItem(withdrawal, customers.get(withdrawal.getCustomerId()),
                        wallets.get(withdrawal.getCustomerId())))
                .toList();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long oldestWaitingDays = customerWithdrawalRequestRepository.findOldestPending(PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(oldest -> daysBetween(oldest.getRequestedAt(), now))
                .orElse(0L);

        return new PendingCustomerWithdrawalPageResponse(
                content,
                withdrawals.getNumber(),
                withdrawals.getSize(),
                withdrawals.getTotalElements(),
                withdrawals.getTotalPages(),
                withdrawals.isFirst(),
                withdrawals.isLast(),
                customerWithdrawalRequestRepository.countPending(),
                money(customerWithdrawalRequestRepository.sumPendingAmount()),
                oldestWaitingDays,
                customerWithdrawalRequestRepository.countPendingRequestedBefore(now.minusDays(1))
        );
    }

    @Transactional
    public WithdrawalActionResponse process(UUID withdrawalId, User admin, ProcessWithdrawalRequest request) {
        String bankTxnRef = validateBankTxnRef(request != null ? request.bankTxnRef() : null);
        String note = normalizeOptional(request != null ? request.processingNote() : null, 500);

        CustomerWithdrawalRequest withdrawal = customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "WITHDRAWAL_NOT_FOUND|Khong tim thay yeu cau rut tien."));

        if (PROCESSED.equals(withdrawal.getStatus()) && bankTxnRef.equals(withdrawal.getBankTxnRef())) {
            return processedReplay(withdrawal);
        }
        ensurePending(withdrawal);
        if (customerWithdrawalRequestRepository.existsByBankTxnRef(bankTxnRef)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DUPLICATE_BANK_TXN_REF|Ma giao dich ngan hang da duoc su dung.");
        }

        walletRepository.insertIfMissing(withdrawal.getCustomerId());
        CustomerWallet wallet = walletRepository.findByCustomerIdForUpdate(withdrawal.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "CUSTOMER_WALLET_NOT_FOUND|Khong tim thay vi khach hang."));

        BigDecimal amount = money(withdrawal.getAmount());
        BigDecimal currentBalance = money(wallet.getBalance());
        if (currentBalance.compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INSUFFICIENT_CURRENT_BALANCE|So du hien tai khong du.");
        }
        if (transactionRepository.existsByTypeAndRelatedCustomerWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "WITHDRAWAL_TRANSACTION_EXISTS|Yeu cau rut tien da co giao dich.");
        }

        BigDecimal balanceAfter = currentBalance.subtract(amount).setScale(0);
        wallet.setBalance(balanceAfter);
        wallet.setTotalWithdrawn(money(wallet.getTotalWithdrawn()).add(amount));
        walletRepository.saveAndFlush(wallet);

        OffsetDateTime processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        withdrawal.setStatus(PROCESSED);
        withdrawal.setProcessedBy(admin.getId());
        withdrawal.setBankTxnRef(bankTxnRef);
        withdrawal.setProcessedAt(processedAt);

        Transaction transaction = Transaction.builder()
                .userId(withdrawal.getCustomerId())
                .type(TransactionType.WITHDRAWAL)
                .amount(amount.negate())
                .relatedCustomerWithdrawalId(withdrawalId)
                .balanceAfter(balanceAfter)
                .description("Rut tien vi khach hang ve tai khoan ngan hang")
                .build();

        try {
            transactionRepository.saveAndFlush(transaction);
            customerWithdrawalRequestRepository.saveAndFlush(withdrawal);
            writeAudit(admin, "CUSTOMER_WITHDRAWAL_PROCESSED", withdrawal,
                    "amount=" + amount + ", balance_after=" + balanceAfter + ", bank_ref=" + bankTxnRef
                            + ", note=" + (note != null ? "present" : "none"));
            createNotification(
                    withdrawal.getCustomerId(),
                    NotificationType.WITHDRAWAL_PROCESSED,
                    "Yeu cau rut tien da duoc xu ly",
                    "Yeu cau rut " + amount.toPlainString() + " VND da duoc chuyen thanh cong.");
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DUPLICATE_WITHDRAWAL_PROCESSING|Yeu cau rut tien da duoc xu ly.", ex);
        }

        return new WithdrawalActionResponse(
                withdrawalId,
                PROCESSED,
                amount,
                balanceAfter,
                "Da ghi nhan chuyen khoan thanh cong"
        );
    }

    @Transactional
    public WithdrawalActionResponse reject(UUID withdrawalId, User admin, RejectWithdrawalRequest request) {
        String reason = validateReason(request != null ? request.reason() : null);
        CustomerWithdrawalRequest withdrawal = customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "WITHDRAWAL_NOT_FOUND|Khong tim thay yeu cau rut tien."));

        if (REJECTED.equals(withdrawal.getStatus()) && reason.equals(withdrawal.getRejectionReason())) {
            return new WithdrawalActionResponse(
                    withdrawal.getId(),
                    REJECTED,
                    money(withdrawal.getAmount()),
                    null,
                    "Da tu choi yeu cau rut tien"
            );
        }
        ensurePending(withdrawal);

        withdrawal.setStatus(REJECTED);
        withdrawal.setRejectionReason(reason);
        withdrawal.setProcessedBy(admin.getId());
        withdrawal.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));

        customerWithdrawalRequestRepository.saveAndFlush(withdrawal);
        writeAudit(admin, "CUSTOMER_WITHDRAWAL_REJECTED", withdrawal, "amount=" + money(withdrawal.getAmount()));
        createNotification(
                withdrawal.getCustomerId(),
                NotificationType.WITHDRAWAL_REJECTED,
                "Yeu cau rut tien bi tu choi",
                "Yeu cau rut " + money(withdrawal.getAmount()).toPlainString() + " VND bi tu choi: " + reason);

        return new WithdrawalActionResponse(
                withdrawal.getId(),
                REJECTED,
                money(withdrawal.getAmount()),
                null,
                "Da tu choi yeu cau rut tien"
        );
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    private WithdrawalActionResponse processedReplay(CustomerWithdrawalRequest withdrawal) {
        BigDecimal balanceAfter = transactionRepository
                .findByTypeAndRelatedCustomerWithdrawalId(TransactionType.WITHDRAWAL, withdrawal.getId())
                .map(Transaction::getBalanceAfter)
                .map(this::money)
                .orElse(null);
        return new WithdrawalActionResponse(
                withdrawal.getId(),
                PROCESSED,
                money(withdrawal.getAmount()),
                balanceAfter,
                "Da ghi nhan chuyen khoan thanh cong"
        );
    }

    private AdminCustomerWithdrawalItemResponse toPendingItem(
            CustomerWithdrawalRequest withdrawal,
            User customer,
            CustomerWallet wallet
    ) {
        BigDecimal walletBalance = wallet != null ? money(wallet.getBalance()) : BigDecimal.ZERO.setScale(0);
        List<String> blockingReasons = new ArrayList<>();
        if (customer == null) {
            blockingReasons.add("CUSTOMER_NOT_FOUND");
        }
        if (walletBalance.compareTo(money(withdrawal.getAmount())) < 0) {
            blockingReasons.add("INSUFFICIENT_CURRENT_BALANCE");
        }
        if (withdrawal.getBankAccountNumber() == null || withdrawal.getBankAccountNumber().isBlank()) {
            blockingReasons.add("BANK_ACCOUNT_MISSING");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new AdminCustomerWithdrawalItemResponse(
                withdrawal.getId(),
                withdrawal.getCustomerId(),
                customer != null ? customer.getFullName() : null,
                customer != null ? customer.getPhone() : null,
                money(withdrawal.getAmount()),
                withdrawal.getBankCode(),
                withdrawal.getBankNameSnapshot(),
                maskAccount(withdrawal.getBankAccountNumber()),
                withdrawal.getRequestedAt(),
                daysBetween(withdrawal.getRequestedAt(), now),
                walletBalance,
                blockingReasons.isEmpty(),
                blockingReasons
        );
    }

    private Map<UUID, User> loadUsers(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private Map<UUID, CustomerWallet> loadWallets(Set<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        return walletRepository.findByCustomerIdIn(customerIds)
                .stream()
                .collect(Collectors.toMap(CustomerWallet::getCustomerId, wallet -> wallet));
    }

    private void ensurePending(CustomerWithdrawalRequest withdrawal) {
        if (!PENDING.equals(withdrawal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_WITHDRAWAL_TRANSITION|Yeu cau rut tien da duoc xu ly.");
        }
    }

    private String validateBankTxnRef(String value) {
        String normalized = normalizeRequired(value, "INVALID_BANK_TXN_REF|Ma giao dich ngan hang bat buoc.");
        if (normalized.length() < 6 || normalized.length() > 100 || !BANK_REF_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_BANK_TXN_REF|Ma giao dich ngan hang khong hop le.");
        }
        return normalized;
    }

    private String validateReason(String value) {
        String normalized = normalizeRequired(value, "INVALID_REJECTION_REASON|Ly do tu choi bat buoc.");
        boolean hasLetter = normalized.chars().anyMatch(Character::isLetter);
        if (normalized.length() < 10 || normalized.length() > 500 || !hasLetter) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_REJECTION_REASON|Ly do tu choi khong hop le.");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String error) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, error);
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Gia tri vuot qua do dai cho phep.");
        }
        return normalized;
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

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(0);
        }
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private long daysBetween(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return Math.max(0L, Duration.between(from, to).toDays());
    }

    private String maskAccount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        String last4 = trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4);
        return "******" + last4;
    }

    private void writeAudit(User admin, String action, CustomerWithdrawalRequest withdrawal, String detail) {
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorId(admin != null ? admin.getId() : null)
                .actorEmail(admin != null ? admin.getEmail() : null)
                .action(action)
                .entityType("CUSTOMER_WITHDRAWAL_REQUEST")
                .entityId(withdrawal.getId().toString())
                .detail(detail)
                .build());
    }

    private void createNotification(UUID userId, String type, String title, String message) {
        Objects.requireNonNull(userId, "userId");
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .isRead(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }
}
