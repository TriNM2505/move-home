package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.finance.AdminTransactionResponse;
import vn.movehome.backend.driver.finance.WithdrawalRequest;
import vn.movehome.backend.driver.finance.WithdrawalRequestRepository;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminTransactionService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    @Transactional(readOnly = true)
    public Page<AdminTransactionResponse> findTransactions(
            String type,
            Instant from,
            Instant to,
            UUID userId,
            int page,
            int size
    ) {
        validatePage(page, size);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_DATE_RANGE|Khoang thoi gian khong hop le.");
        }

        TransactionType transactionType = parseType(type);
        Specification<Transaction> specification = (root, query, cb) -> cb.conjunction();
        if (transactionType != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("type"), transactionType));
        }
        if (from != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThan(root.get("createdAt"), to));
        }
        if (userId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<Transaction> transactions = transactionRepository.findAll(specification, pageable);

        Map<UUID, User> users = loadUsers(transactions.getContent().stream()
                .map(Transaction::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<UUID, ServiceOrder> orders = loadOrders(transactions.getContent().stream()
                .map(Transaction::getRelatedOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<UUID, WithdrawalRequest> withdrawals = loadWithdrawals(transactions.getContent().stream()
                .map(Transaction::getRelatedWithdrawalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        return transactions.map(transaction -> toResponse(
                transaction,
                users.get(transaction.getUserId()),
                orders.get(transaction.getRelatedOrderId()),
                withdrawals.get(transaction.getRelatedWithdrawalId())
        ));
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    private TransactionType parseType(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return TransactionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNSUPPORTED_TRANSACTION_TYPE|Loai giao dich khong duoc ho tro.");
        }
    }

    private AdminTransactionResponse toResponse(
            Transaction transaction,
            User user,
            ServiceOrder order,
            WithdrawalRequest withdrawal
    ) {
        return new AdminTransactionResponse(
                transaction.getId(),
                transaction.getType().name(),
                typeLabel(transaction.getType()),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getUserId(),
                user != null ? user.getFullName() : null,
                user != null && user.getRole() != null ? user.getRole().name() : null,
                user != null ? maskEmail(user.getEmail()) : null,
                transaction.getRelatedOrderId(),
                order != null ? order.getOrderCode() : null,
                transaction.getRelatedWithdrawalId(),
                transaction.getRelatedDisputeId(),
                maskRef(transaction.getVnpayTxnRef()),
                withdrawal != null ? maskRef(withdrawal.getBankTxnRef()) : null,
                transaction.getDescription(),
                toOffsetDateTime(transaction.getCreatedAt())
        );
    }

    private Map<UUID, User> loadUsers(Collection<UUID> ids) {
        // HashMap thay vi Map.of(): cho phep .get(null) tra null (giao dich khong gan user/don/rut tien)
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return userRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private Map<UUID, ServiceOrder> loadOrders(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return orderRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(ServiceOrder::getId, order -> order));
    }

    private Map<UUID, WithdrawalRequest> loadWithdrawals(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return withdrawalRequestRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(WithdrawalRequest::getId, withdrawal -> withdrawal));
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

    private String typeLabel(TransactionType type) {
        return switch (type) {
            case DEPOSIT_TOP_UP -> "Dat coc tai xe";
            case DEPOSIT_REFUND -> "Hoan coc tai xe";
            case ORDER_PAYMENT -> "Thanh toan don";
            case WALLET_TOP_UP -> "Nap vi khach hang";
            case DRIVER_EARNING -> "Thu nhap tai xe";
            case PLATFORM_FEE -> "Phi nen tang";
            case DAMAGE_DEDUCTION -> "Khau tru khieu nai";
            case WITHDRAWAL -> "Rut tien";
            case REFUND -> "Hoan tien";
        };
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String maskRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}
