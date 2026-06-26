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
import vn.movehome.backend.dto.admin.finance.AdminWithdrawalItemResponse;
import vn.movehome.backend.dto.admin.finance.PendingWithdrawalPageResponse;
import vn.movehome.backend.dto.admin.finance.ProcessWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.RejectWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.WithdrawalActionResponse;
import vn.movehome.backend.driver.finance.DriverWallet;
import vn.movehome.backend.driver.finance.DriverWalletRepository;
import vn.movehome.backend.driver.finance.WithdrawalRequest;
import vn.movehome.backend.driver.finance.WithdrawalRequestRepository;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.AuditLogRepository;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

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

@Service
@RequiredArgsConstructor
public class AdminWithdrawalService {

    private static final String PENDING = "PENDING";
    private static final String PROCESSED = "PROCESSED";
    private static final String REJECTED = "REJECTED";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern BANK_REF_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]+$");

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final DriverWalletRepository driverWalletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public PendingWithdrawalPageResponse getPending(int page, int size) {
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "requestedAt").and(Sort.by(Sort.Direction.ASC, "id"))
        );
        Page<WithdrawalRequest> withdrawals = withdrawalRequestRepository.findByStatus(PENDING, pageable);
        Map<UUID, User> drivers = loadUsers(withdrawals.getContent().stream()
                .map(WithdrawalRequest::getDriverId)
                .collect(Collectors.toSet()));
        Map<UUID, DriverWallet> wallets = loadWallets(withdrawals.getContent().stream()
                .map(WithdrawalRequest::getDriverId)
                .collect(Collectors.toSet()));

        List<AdminWithdrawalItemResponse> content = withdrawals.getContent()
                .stream()
                .map(withdrawal -> toPendingItem(withdrawal, drivers.get(withdrawal.getDriverId()),
                        wallets.get(withdrawal.getDriverId())))
                .toList();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long oldestWaitingDays = withdrawalRequestRepository.findOldestPending(PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(oldest -> daysBetween(oldest.getRequestedAt(), now))
                .orElse(0L);

        return new PendingWithdrawalPageResponse(
                content,
                withdrawals.getNumber(),
                withdrawals.getSize(),
                withdrawals.getTotalElements(),
                withdrawals.getTotalPages(),
                withdrawals.isFirst(),
                withdrawals.isLast(),
                withdrawalRequestRepository.countPending(),
                money(withdrawalRequestRepository.sumPendingAmount()),
                oldestWaitingDays,
                withdrawalRequestRepository.countPendingRequestedBefore(now.minusDays(1))
        );
    }

    @Transactional
    public WithdrawalActionResponse process(UUID withdrawalId, User admin, ProcessWithdrawalRequest request) {
        String bankTxnRef = validateBankTxnRef(request != null ? request.bankTxnRef() : null);
        String note = normalizeOptional(request != null ? request.processingNote() : null, 500);

        WithdrawalRequest withdrawal = withdrawalRequestRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "WITHDRAWAL_NOT_FOUND|Khong tim thay yeu cau rut tien."));

        if (PROCESSED.equals(withdrawal.getStatus()) && bankTxnRef.equals(withdrawal.getBankTxnRef())) {
            return processedReplay(withdrawal);
        }
        ensurePending(withdrawal);
        if (admin != null && admin.getId() != null && admin.getId().equals(withdrawal.getDriverId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_WITHDRAWAL_ACTOR|Admin khong duoc xu ly yeu cau cua chinh minh.");
        }
        if (withdrawalRequestRepository.existsByBankTxnRef(bankTxnRef)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DUPLICATE_BANK_TXN_REF|Ma giao dich ngan hang da duoc su dung.");
        }

        driverWalletRepository.insertIfMissing(withdrawal.getDriverId());
        DriverWallet wallet = driverWalletRepository.findByDriverIdForUpdate(withdrawal.getDriverId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "DRIVER_WALLET_NOT_FOUND|Khong tim thay vi tai xe."));

        BigDecimal amount = money(withdrawal.getAmount());
        BigDecimal currentBalance = money(wallet.getBalance());
        if (currentBalance.compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INSUFFICIENT_CURRENT_BALANCE|So du hien tai khong du.");
        }
        if (transactionRepository.existsByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "WITHDRAWAL_TRANSACTION_EXISTS|Yeu cau rut tien da co giao dich.");
        }

        BigDecimal balanceAfter = currentBalance.subtract(amount).setScale(0);
        wallet.setBalance(balanceAfter);
        wallet.setTotalWithdrawn(money(wallet.getTotalWithdrawn()).add(amount));
        driverWalletRepository.saveAndFlush(wallet);

        OffsetDateTime processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        withdrawal.setStatus(PROCESSED);
        withdrawal.setProcessedBy(admin.getId());
        withdrawal.setBankTxnRef(bankTxnRef);
        withdrawal.setProcessedAt(processedAt);

        Transaction transaction = Transaction.builder()
                .userId(withdrawal.getDriverId())
                .type(TransactionType.WITHDRAWAL)
                .amount(amount.negate())
                .relatedWithdrawalId(withdrawalId)
                .balanceAfter(balanceAfter)
                .description("Rut tien ve tai khoan ngan hang")
                .build();

        try {
            transactionRepository.saveAndFlush(transaction);
            withdrawalRequestRepository.saveAndFlush(withdrawal);
            writeAudit(admin, "WITHDRAWAL_PROCESSED", withdrawal,
                    "amount=" + amount + ", balance_after=" + balanceAfter + ", bank_ref=" + bankTxnRef
                            + ", note=" + (note != null ? "present" : "none"));
            createNotification(
                    withdrawal.getDriverId(),
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
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findByIdForUpdate(withdrawalId)
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

        withdrawalRequestRepository.saveAndFlush(withdrawal);
        writeAudit(admin, "WITHDRAWAL_REJECTED", withdrawal, "amount=" + money(withdrawal.getAmount()));
        createNotification(
                withdrawal.getDriverId(),
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

    private WithdrawalActionResponse processedReplay(WithdrawalRequest withdrawal) {
        BigDecimal balanceAfter = transactionRepository
                .findByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawal.getId())
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

    private AdminWithdrawalItemResponse toPendingItem(
            WithdrawalRequest withdrawal,
            User driver,
            DriverWallet wallet
    ) {
        BigDecimal walletBalance = wallet != null ? money(wallet.getBalance()) : BigDecimal.ZERO.setScale(0);
        List<String> blockingReasons = new ArrayList<>();
        if (driver == null) {
            blockingReasons.add("DRIVER_NOT_FOUND");
        } else if (driver.getStatus() != UserStatus.ACTIVE) {
            blockingReasons.add("DRIVER_NOT_ACTIVE");
        }
        if (walletBalance.compareTo(money(withdrawal.getAmount())) < 0) {
            blockingReasons.add("INSUFFICIENT_CURRENT_BALANCE");
        }
        if (withdrawal.getBankAccountNumber() == null || withdrawal.getBankAccountNumber().isBlank()) {
            blockingReasons.add("BANK_ACCOUNT_MISSING");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new AdminWithdrawalItemResponse(
                withdrawal.getId(),
                withdrawal.getDriverId(),
                driver != null ? driver.getFullName() : null,
                driver != null ? driver.getPhone() : null,
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

    private Map<UUID, DriverWallet> loadWallets(Set<UUID> driverIds) {
        if (driverIds == null || driverIds.isEmpty()) {
            return Map.of();
        }
        return driverWalletRepository.findByDriverIdIn(driverIds)
                .stream()
                .collect(Collectors.toMap(DriverWallet::getDriverId, wallet -> wallet));
    }

    private void ensurePending(WithdrawalRequest withdrawal) {
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

    private void writeAudit(User admin, String action, WithdrawalRequest withdrawal, String detail) {
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorId(admin != null ? admin.getId() : null)
                .actorEmail(admin != null ? admin.getEmail() : null)
                .action(action)
                .entityType("WITHDRAWAL_REQUEST")
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
