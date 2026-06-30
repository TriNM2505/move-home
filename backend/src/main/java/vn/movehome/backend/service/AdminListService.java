package vn.movehome.backend.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import vn.movehome.backend.driver.finance.WithdrawalRequestRepository;
import vn.movehome.backend.dto.admin.list.CustomerListItem;
import vn.movehome.backend.dto.admin.list.DriverListItem;
import vn.movehome.backend.dto.admin.list.OrderListItem;
import vn.movehome.backend.dto.admin.list.WithdrawalListItem;
import vn.movehome.backend.dto.admin.list.WithdrawalListItemRaw;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminListService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final int MAX_DATE_RANGE_DAYS = 366;
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50, 100);

    private static final Set<String> ORDER_STATUS_ALLOWED = Set.of(
            "ALL",
            "PENDING",
            "PENDING_PAYMENT",
            "CONFIRMED",
            "ASSIGNED",
            "ACCEPTED",
            "IN_PROGRESS",
            "COMPLETED",
            "CANCELLED",
            "DISPUTED",
            "IN_DISPUTE");
    private static final Set<String> DRIVER_STATUS_ALLOWED = Set.of(
            "ALL", "ACTIVE", "PENDING_VERIFY", "PENDING_DOCUMENTS", "PENDING_DEPOSIT",
            "PENDING_APPROVAL", "REJECTED", "SUSPENDED", "LOCKED");
    private static final Set<String> CUSTOMER_STATUS_ALLOWED = Set.of(
            "ALL", "ACTIVE", "PENDING_VERIFY", "SUSPENDED", "LOCKED");
    private static final Set<String> WITHDRAWAL_STATUS_ALLOWED = Set.of(
            "ALL", "PENDING", "PROCESSED", "REJECTED", "CANCELLED");

    private static final Map<String, String> ORDER_SORT_MAP = Map.of(
            "created_at", "so.createdAt",
            "total_quote", "so.totalQuote",
            "status", "so.status",
            "scheduled_at", "so.scheduledAt");
    private static final Map<String, String> DRIVER_SORT_MAP = Map.of(
            "created_at", "u.createdAt",
            "total_earnings", "dw.totalEarned",
            "average_rating", "dp.averageRating",
            "total_completed_orders", "dp.totalOrdersCompleted",
            "last_active_at", "u.createdAt" // TODO Sprint 6: thay khi co cot last_active_at that.
    );
    private static final Map<String, String> CUSTOMER_SORT_MAP = Map.of(
            "created_at", "u.createdAt",
            "total_orders", "u.createdAt", // TODO Sprint 6: sort theo subquery COUNT, dung created_at tam.
            "total_spent", "cw.totalSpent",
            "last_active_at", "u.createdAt" // TODO Sprint 6.
    );
    private static final Map<String, String> WITHDRAWAL_SORT_MAP = Map.of(
            "requested_at", "wr.requestedAt",
            "processed_at", "wr.processedAt",
            "amount", "wr.amount",
            "status", "wr.status");

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    // Spec 011/012/016 FR de xuat audit GET. Theo chot Leader Sprint 5: GET
    // read-only khong audit, chi POST/PUT moi audit.

    public Page<OrderListItem> listOrders(String status, String search, String dateFrom,
            String dateTo, int page, int size, String sort) {
        validatePageSize(page, size);
        String normSearch = validateAndNormalizeSearch(search);
        DateRange range = parseAndValidateDateRange(dateFrom, dateTo);
        String schemaStatus = validateOrderStatus(status);
        Pageable pageable = buildPageable(page, size, sort, ORDER_SORT_MAP, "so.id");
        return orderRepository.findAdminOrderList(
                schemaStatus, normSearch, range.from(), range.to(), pageable);
    }

    public Page<DriverListItem> listDrivers(String status, String search,
            int page, int size, String sort) {
        validatePageSize(page, size);
        String normSearch = validateAndNormalizeSearch(search);
        String schemaStatus = validateDriverStatus(status);
        Pageable pageable = buildPageable(page, size, sort, DRIVER_SORT_MAP, "u.id");
        return userRepository.findAdminDriverList(schemaStatus, normSearch, pageable);
    }

    public Page<CustomerListItem> listCustomers(String status, String search,
            int page, int size, String sort) {
        validatePageSize(page, size);
        String normSearch = validateAndNormalizeSearch(search);
        String schemaStatus = validateCustomerStatus(status);
        Pageable pageable = buildPageable(page, size, sort, CUSTOMER_SORT_MAP, "u.id");
        return userRepository.findAdminCustomerList(schemaStatus, normSearch, pageable);
    }

    public Page<WithdrawalListItem> listWithdrawals(String status, String search,
            String dateFrom, String dateTo, int page, int size, String sort) {
        validatePageSize(page, size);
        String normSearch = validateAndNormalizeSearch(search);
        DateRange range = parseAndValidateDateRange(dateFrom, dateTo);
        String schemaStatus = validateWithdrawalStatus(status);
        Pageable pageable = buildPageable(page, size, sort, WITHDRAWAL_SORT_MAP, "wr.id");
        Page<WithdrawalListItemRaw> rawPage = withdrawalRequestRepository
                .findAdminWithdrawalList(schemaStatus, normSearch, range.from(), range.to(), pageable);
        return rawPage.map(this::maskWithdrawal);
    }

    private void validatePageSize(int page, int size) {
        if (page < 0 || !ALLOWED_SIZES.contains(size)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_PAGINATION|Tham số phân trang không hợp lệ.");
        }
    }

    private String validateAndNormalizeSearch(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_SEARCH_TERM|Từ khóa tìm kiếm không hợp lệ.");
        }

        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isISOControl(normalized.charAt(i))) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "INVALID_SEARCH_TERM|Từ khóa tìm kiếm không hợp lệ.");
            }
        }

        return normalized;
    }

    private DateRange parseAndValidateDateRange(String fromStr, String toStr) {
        if (fromStr == null && toStr == null) {
            return new DateRange(null, null);
        }

        try {
            LocalDate fromDate = fromStr != null ? LocalDate.parse(fromStr) : null;
            LocalDate toDate = toStr != null ? LocalDate.parse(toStr) : null;

            if (fromDate != null && toDate != null) {
                if (fromDate.isAfter(toDate)) {
                    throw invalidDateRange();
                }
                if (ChronoUnit.DAYS.between(fromDate, toDate) > MAX_DATE_RANGE_DAYS) {
                    throw invalidDateRange();
                }
            }

            OffsetDateTime fromUtc = fromDate != null
                    ? fromDate.atStartOfDay(VN_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC)
                    : null;
            OffsetDateTime toUtc = toDate != null
                    ? toDate.plusDays(1).atStartOfDay(VN_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC)
                    : null;
            return new DateRange(fromUtc, toUtc);
        } catch (DateTimeParseException ex) {
            throw invalidDateRange();
        }
    }

    private Pageable buildPageable(int page, int size, String sortRaw,
            Map<String, String> allowlist, String secondaryIdPath) {
        String key;
        Sort.Direction direction;
        if (sortRaw == null || sortRaw.isBlank()) {
            key = allowlist.containsKey("created_at") ? "created_at" : "requested_at";
            direction = Sort.Direction.DESC;
        } else {
            String[] parts = sortRaw.split(",", 2);
            key = parts[0].trim();
            if (parts.length == 2) {
                String dir = parts[1].trim().toLowerCase();
                if ("asc".equals(dir)) {
                    direction = Sort.Direction.ASC;
                } else if ("desc".equals(dir)) {
                    direction = Sort.Direction.DESC;
                } else {
                    throw invalidSort();
                }
            } else {
                direction = Sort.Direction.DESC;
            }
        }

        String jpqlPath = allowlist.get(key);
        if (jpqlPath == null) {
            throw invalidSort();
        }

        Sort sort = JpaSort.unsafe(direction, jpqlPath)
                .and(JpaSort.unsafe(direction, secondaryIdPath));
        return PageRequest.of(page, size, sort);
    }

    private String validateOrderStatus(String status) {
        String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase();
        if ("ALL".equals(normalizedStatus)) {
            return null;
        }
        if (!ORDER_STATUS_ALLOWED.contains(normalizedStatus)) {
            throw invalidStatusFilter();
        }
        return normalizedStatus;
    }

    private String validateDriverStatus(String status) {
        if (status == null || "ALL".equals(status)) {
            return null;
        }
        if (!DRIVER_STATUS_ALLOWED.contains(status)) {
            throw invalidStatusFilter();
        }
        return status;
    }

    private String validateCustomerStatus(String status) {
        if (status == null || "ALL".equals(status)) {
            return null;
        }
        if (!CUSTOMER_STATUS_ALLOWED.contains(status)) {
            throw invalidStatusFilter();
        }
        return status;
    }

    private String validateWithdrawalStatus(String status) {
        if (status == null || "ALL".equals(status)) {
            return null;
        }
        if (!WITHDRAWAL_STATUS_ALLOWED.contains(status)) {
            throw invalidStatusFilter();
        }
        return status;
    }

    private WithdrawalListItem maskWithdrawal(WithdrawalListItemRaw raw) {
        return new WithdrawalListItem(
                raw.id(),
                raw.driverId(),
                raw.driverName(),
                raw.amount(),
                raw.bankName(),
                maskBankAccount(raw.bankAccountNumber()),
                raw.status(),
                raw.requestedAt(),
                raw.processedAt(),
                raw.processorName(),
                maskBankTxnRef(raw.bankTxnRef()));
    }

    private String maskBankAccount(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= 4) {
            return "*".repeat(raw.length());
        }
        int hiddenLen = raw.length() - 4;
        return "*".repeat(hiddenLen) + raw.substring(hiddenLen);
    }

    private String maskBankTxnRef(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= 4) {
            return "*".repeat(raw.length());
        }
        return "***" + raw.substring(raw.length() - 4);
    }

    private ResponseStatusException invalidStatusFilter() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_STATUS_FILTER|Bộ lọc trạng thái không hợp lệ.");
    }

    private ResponseStatusException invalidSort() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_SORT|Cách sắp xếp không hợp lệ.");
    }

    private ResponseStatusException invalidDateRange() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_DATE_RANGE|Khoảng ngày không hợp lệ.");
    }

    private record DateRange(OffsetDateTime from, OffsetDateTime to) {
    }
}
