package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.DriverWallet;
import vn.movehome.backend.driver.finance.DriverWalletRepository;
import vn.movehome.backend.driver.finance.WithdrawalRequestRepository;
import vn.movehome.backend.driver.location.DriverLocation;
import vn.movehome.backend.driver.location.DriverLocationRepository;
import vn.movehome.backend.dto.admin.detail.AuditLogItem;
import vn.movehome.backend.dto.admin.detail.CustomerDetailResponse;
import vn.movehome.backend.dto.admin.detail.CustomerOrderItem;
import vn.movehome.backend.dto.admin.detail.DriverDetailResponse;
import vn.movehome.backend.dto.admin.detail.DriverOrderItem;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.order.OrderRatingRepository;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.AuditLogRepository;
import vn.movehome.backend.repository.DriverDocumentRepository;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;
import vn.movehome.backend.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDetailService {

    private static final int RECENT_ORDERS_LIMIT = 10;
    private static final int RECENT_WITHDRAWALS_LIMIT = 10;
    private static final int RECENT_TRANSACTIONS_LIMIT = 20;
    private static final int ONLINE_THRESHOLD_MINUTES = 5;
    private static final int AUDIT_DATE_RANGE_MAX_DAYS = 366;
    private static final Set<String> BUSY_ORDER_STATUSES = Set.of("ACCEPTED", "IN_PROGRESS");
    private static final List<String> DRIVER_ALLOWED_ACTIONS = List.of("VIEW_AUDIT", "VIEW_ORDER_HISTORY");
    private static final List<String> CUSTOMER_ALLOWED_ACTIONS = List.of("VIEW_AUDIT", "VIEW_ORDER_HISTORY");
    private static final Set<String> AUDIT_ENTITY_TYPES = Set.of("orders", "drivers", "customers");
    private static final Set<String> ORDER_HISTORY_STATUSES = Set.of(
            "ALL", "PENDING", "ACCEPTED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "DISPUTED"
    );
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20, 50, 100);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final DriverWalletRepository driverWalletRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final OrderRepository orderRepository;
    private final OrderRatingRepository orderRatingRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AuditLogRepository auditLogRepository;

    // Spec 011/012/016 FR de xuat audit GET. Theo chot Leader Sprint 5:
    // GET read-only khong audit, chi POST/PUT moi audit.

    public DriverDetailResponse driverDetail(UUID driverId) {
        User user = userRepository.findAdminDriverDetailUser(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DRIVER_NOT_FOUND|Không tìm thấy tài xế."));

        DriverProfile profile = driverProfileRepository.findByUserId(driverId).orElse(null);

        DriverDetailResponse.UserSection userSection = buildUserSection(user);
        DriverDetailResponse.ProfileSection profileSection = buildProfileSection(profile);
        DriverDetailResponse.DocumentsSummary documentsSummary = buildDocumentsSummary(driverId);
        List<DriverDetailResponse.VehicleItem> vehicles = buildVehicles(profile);
        DriverDetailResponse.DepositSection deposit = buildDeposit(profile);
        DriverDetailResponse.WalletSection wallet = buildWallet(driverId);
        DriverDetailResponse.StatsSection stats = buildDriverStats(driverId);
        DriverDetailResponse.RatingDistribution rating = buildRatingDistribution(driverId);
        List<DriverDetailResponse.RecentOrderItem> recentOrders = orderRepository
                .findRecentOrdersByDriver(driverId, PageRequest.of(0, RECENT_ORDERS_LIMIT));
        List<DriverDetailResponse.RecentWithdrawalItem> recentWithdrawals = withdrawalRequestRepository
                .findRecentWithdrawalsByDriver(driverId, PageRequest.of(0, RECENT_WITHDRAWALS_LIMIT));
        OnlineStatusResult onlineResult = computeOnlineStatus(driverId);

        return new DriverDetailResponse(
                userSection,
                profileSection,
                documentsSummary,
                vehicles,
                deposit,
                wallet,
                stats,
                rating,
                recentOrders,
                recentWithdrawals,
                onlineResult.status(),
                onlineResult.location(),
                DRIVER_ALLOWED_ACTIONS
        );
    }

    public CustomerDetailResponse customerDetail(UUID customerId) {
        User user = userRepository.findAdminCustomerDetailUser(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "CUSTOMER_NOT_FOUND|Khong tim thay khach hang."));

        CustomerDetailResponse.UserSection userSection = buildCustomerUserSection(user);
        CustomerDetailResponse.StatsSection stats = buildCustomerStats(customerId);
        List<CustomerDetailResponse.RecentOrderItem> recentOrders = orderRepository
                .findRecentOrdersByCustomer(customerId, PageRequest.of(0, RECENT_ORDERS_LIMIT));
        CustomerDetailResponse.WalletSummary walletSummary = buildCustomerWalletSummary(customerId);
        List<CustomerDetailResponse.RecentWalletTransactionItem> recentTransactions = walletTransactionRepository
                .findRecentByUserId(customerId, PageRequest.of(0, RECENT_TRANSACTIONS_LIMIT))
                .stream()
                .map(this::maskTransactionReference)
                .toList();
        List<CustomerDetailResponse.DistrictActivityItem> districtActivity = buildDistrictActivity(customerId);

        return new CustomerDetailResponse(
                userSection,
                stats,
                recentOrders,
                walletSummary,
                recentTransactions,
                List.of(),
                districtActivity,
                List.of(),
                CUSTOMER_ALLOWED_ACTIONS
        );
    }

    public Page<AuditLogItem> entityAuditLog(
            String entityType,
            UUID entityId,
            String eventType,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size) {
        if (!AUDIT_ENTITY_TYPES.contains(entityType)) {
            throw invalidAuditFilter("entityType khong hop le");
        }
        validatePageSize(page, size);
        validateEntityExists(entityType, entityId);

        String schemaEntityType = switch (entityType) {
            case "orders" -> "SERVICE_ORDER";
            case "drivers", "customers" -> "USER";
            default -> throw invalidAuditFilter("entityType khong hop le");
        };

        OffsetDateTime from = null;
        OffsetDateTime to = null;
        if (dateFrom != null && dateTo != null) {
            if (dateFrom.isAfter(dateTo)) {
                throw invalidDateRange();
            }
            if (ChronoUnit.DAYS.between(dateFrom, dateTo) > AUDIT_DATE_RANGE_MAX_DAYS) {
                throw invalidDateRange();
            }
            from = dateFrom.atStartOfDay(VN_ZONE).toOffsetDateTime();
            to = dateTo.plusDays(1).atStartOfDay(VN_ZONE).toOffsetDateTime();
        } else if (dateFrom != null || dateTo != null) {
            throw invalidDateRange();
        }

        String normalizedEventType = eventType != null && !eventType.isBlank() ? eventType.trim() : null;
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return auditLogRepository.findAdminEntityAuditLog(
                schemaEntityType,
                entityId.toString(),
                normalizedEventType,
                from,
                to,
                pageable
        );
    }

    public Page<DriverOrderItem> driverOrderHistory(UUID driverId, String status, int page, int size) {
        if (userRepository.findAdminDriverDetailUser(driverId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "DRIVER_NOT_FOUND|Khong tim thay tai xe.");
        }
        validatePageSize(page, size);

        String schemaStatus = validateOrderHistoryStatus(status);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return orderRepository.findDriverOrderHistory(driverId, schemaStatus, pageable);
    }

    public Page<CustomerOrderItem> customerOrderHistory(UUID customerId, String status, int page, int size) {
        if (userRepository.findAdminCustomerDetailUser(customerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "CUSTOMER_NOT_FOUND|Khong tim thay khach hang.");
        }
        validatePageSize(page, size);

        String schemaStatus = validateOrderHistoryStatus(status);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return orderRepository.findCustomerOrderHistory(customerId, schemaStatus, pageable);
    }

    private DriverDetailResponse.UserSection buildUserSection(User user) {
        return new DriverDetailResponse.UserSection(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                maskPhone(user.getPhone()),
                user.getStatus() != null ? user.getStatus().name() : null,
                user.isEmailVerified(),
                toOffsetDateTime(user.getCreatedAt()),
                null
        );
    }

    private CustomerDetailResponse.UserSection buildCustomerUserSection(User user) {
        return new CustomerDetailResponse.UserSection(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                maskPhone(user.getPhone()),
                user.getStatus() != null ? user.getStatus().name() : null,
                user.isEmailVerified(),
                toOffsetDateTime(user.getCreatedAt()),
                null
        );
    }

    private DriverDetailResponse.ProfileSection buildProfileSection(DriverProfile profile) {
        if (profile == null) {
            return new DriverDetailResponse.ProfileSection(null, null, null, null, null);
        }
        return new DriverDetailResponse.ProfileSection(
                profile.getLicenseNumber(),
                profile.getLicenseClass(),
                profile.getLicenseExpiryDate(),
                profile.getOnboardingCompletedAt(),
                profile.getApprovedAt()
        );
    }

    private DriverDetailResponse.DocumentsSummary buildDocumentsSummary(UUID driverId) {
        List<DriverDocumentRepository.DocTypeCount> counts = driverDocumentRepository.countDocumentsByType(driverId);
        long driving = 0;
        long registration = 0;
        long photo = 0;
        for (DriverDocumentRepository.DocTypeCount count : counts) {
            switch (count.getDocType()) {
                case "DRIVING_LICENSE" -> driving = safeLong(count.getCount());
                case "VEHICLE_REGISTRATION" -> registration = safeLong(count.getCount());
                case "VEHICLE_PHOTO" -> photo = safeLong(count.getCount());
                default -> {
                }
            }
        }
        return new DriverDetailResponse.DocumentsSummary(driving, registration, photo, driving + registration + photo);
    }

    private List<DriverDetailResponse.VehicleItem> buildVehicles(DriverProfile profile) {
        if (profile == null || profile.getVehiclePlate() == null) {
            return List.of();
        }
        return List.of(new DriverDetailResponse.VehicleItem(
                profile.getVehiclePlate(),
                profile.getVehicleType(),
                profile.getVehicleCapacityKg(),
                true
        ));
    }

    private DriverDetailResponse.DepositSection buildDeposit(DriverProfile profile) {
        if (profile == null) {
            return new DriverDetailResponse.DepositSection(null, null, "MISSING");
        }
        String status = profile.getDepositPaidAt() != null ? "PAID" : "UNPAID";
        return new DriverDetailResponse.DepositSection(
                profile.getDepositAmount(),
                profile.getDepositPaidAt(),
                status
        );
    }

    private DriverDetailResponse.WalletSection buildWallet(UUID driverId) {
        DriverWallet wallet = driverWalletRepository.findByDriverId(driverId).orElse(null);
        if (wallet == null) {
            return new DriverDetailResponse.WalletSection(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal totalWithdrawn = withdrawalRequestRepository.sumProcessedAmountByDriver(driverId);
        return new DriverDetailResponse.WalletSection(
                wallet.getBalance(),
                wallet.getTotalEarned(),
                totalWithdrawn
        );
    }

    private DriverDetailResponse.StatsSection buildDriverStats(UUID driverId) {
        long completed = orderRepository.countByDriverIdAndStatusAndDeletedAtIsNull(driverId, "COMPLETED");
        long cancelled = orderRepository.countByDriverIdAndStatusAndDeletedAtIsNull(driverId, "CANCELLED");
        long disputed = orderRepository.countByDriverIdAndStatusAndDeletedAtIsNull(driverId, "DISPUTED");
        BigDecimal avgRating = orderRatingRepository.averageRatingByDriver(driverId)
                .orElse(BigDecimal.ZERO);
        long totalRatings = orderRatingRepository.countRatingsByDriverGroupByStar(driverId).stream()
                .mapToLong(count -> safeLong(count.getCount()))
                .sum();
        return new DriverDetailResponse.StatsSection(
                completed,
                cancelled,
                disputed,
                avgRating,
                totalRatings
        );
    }

    private DriverDetailResponse.RatingDistribution buildRatingDistribution(UUID driverId) {
        List<OrderRatingRepository.RatingStarCount> counts = orderRatingRepository
                .countRatingsByDriverGroupByStar(driverId);
        long one = 0;
        long two = 0;
        long three = 0;
        long four = 0;
        long five = 0;
        for (OrderRatingRepository.RatingStarCount count : counts) {
            switch (count.getStar()) {
                case 1 -> one = safeLong(count.getCount());
                case 2 -> two = safeLong(count.getCount());
                case 3 -> three = safeLong(count.getCount());
                case 4 -> four = safeLong(count.getCount());
                case 5 -> five = safeLong(count.getCount());
                default -> {
                }
            }
        }
        long total = one + two + three + four + five;
        BigDecimal average = total > 0
                ? BigDecimal.valueOf(one + two * 2 + three * 3 + four * 4 + five * 5)
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new DriverDetailResponse.RatingDistribution(one, two, three, four, five, total, average);
    }

    private CustomerDetailResponse.StatsSection buildCustomerStats(UUID customerId) {
        long total = orderRepository.countByCustomerIdAndDeletedAtIsNull(customerId);
        long completed = orderRepository.countByCustomerIdAndStatusAndDeletedAtIsNull(customerId, "COMPLETED");
        long cancelled = orderRepository.countByCustomerIdAndStatusAndDeletedAtIsNull(customerId, "CANCELLED");
        long disputed = orderRepository.countByCustomerIdAndStatusAndDeletedAtIsNull(customerId, "DISPUTED");
        BigDecimal totalSpent = orderRepository.sumCompletedTotalQuoteByCustomer(customerId);
        OffsetDateTime firstOrderAt = orderRepository.findFirstOrderAtByCustomer(customerId).orElse(null);
        OffsetDateTime lastOrderAt = orderRepository.findLastOrderAtByCustomer(customerId).orElse(null);
        return new CustomerDetailResponse.StatsSection(
                total,
                completed,
                cancelled,
                disputed,
                totalSpent,
                firstOrderAt,
                lastOrderAt
        );
    }

    private CustomerDetailResponse.WalletSummary buildCustomerWalletSummary(UUID customerId) {
        CustomerWallet wallet = walletRepository.findByCustomerId(customerId).orElse(null);
        BigDecimal totalToppedUp = walletTransactionRepository.sumTopUpByUserId(customerId);
        BigDecimal safeTotalToppedUp = totalToppedUp != null ? totalToppedUp : BigDecimal.ZERO;
        if (wallet == null) {
            return new CustomerDetailResponse.WalletSummary(
                    BigDecimal.ZERO,
                    safeTotalToppedUp,
                    BigDecimal.ZERO
            );
        }
        return new CustomerDetailResponse.WalletSummary(
                wallet.getBalance(),
                safeTotalToppedUp,
                wallet.getTotalSpent()
        );
    }

    private CustomerDetailResponse.RecentWalletTransactionItem maskTransactionReference(
            CustomerDetailResponse.RecentWalletTransactionItem item) {
        String masked = item.referenceMasked() == null ? null : maskReference(item.referenceMasked());
        return new CustomerDetailResponse.RecentWalletTransactionItem(
                item.id(),
                item.type(),
                item.amount(),
                item.balanceAfter(),
                item.createdAt(),
                masked
        );
    }

    private List<CustomerDetailResponse.DistrictActivityItem> buildDistrictActivity(UUID customerId) {
        List<OrderRepository.DistrictCount> pickups = orderRepository.countPickupDistrictsByCustomer(customerId);
        List<OrderRepository.DistrictCount> dropoffs = orderRepository.countDropoffDistrictsByCustomer(customerId);

        Map<String, long[]> merged = new LinkedHashMap<>();
        for (OrderRepository.DistrictCount pickup : pickups) {
            merged.computeIfAbsent(pickup.getDistrict(), key -> new long[2])[0] = safeLong(pickup.getCount());
        }
        for (OrderRepository.DistrictCount dropoff : dropoffs) {
            merged.computeIfAbsent(dropoff.getDistrict(), key -> new long[2])[1] = safeLong(dropoff.getCount());
        }

        return merged.entrySet().stream()
                .map(entry -> new CustomerDetailResponse.DistrictActivityItem(
                        entry.getKey(),
                        entry.getValue()[0],
                        entry.getValue()[1]
                ))
                .toList();
    }

    private OnlineStatusResult computeOnlineStatus(UUID driverId) {
        DriverLocation location = driverLocationRepository.findByDriverId(driverId).orElse(null);
        if (location == null || location.getUpdatedAt() == null) {
            return new OnlineStatusResult("OFFLINE", null);
        }

        Instant threshold = Instant.now().minus(ONLINE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        if (location.getUpdatedAt().isBefore(threshold)) {
            return new OnlineStatusResult("OFFLINE", null);
        }

        UUID activeOrderId = location.getCurrentOrderId();
        if (activeOrderId == null) {
            return new OnlineStatusResult("ONLINE", null);
        }

        String orderStatus = orderRepository.findById(activeOrderId)
                .map(ServiceOrder::getStatus)
                .orElse(null);
        if (orderStatus != null && BUSY_ORDER_STATUSES.contains(orderStatus)) {
            DriverDetailResponse.LastKnownLocation lastKnownLocation = new DriverDetailResponse.LastKnownLocation(
                    location.getLat(),
                    location.getLng(),
                    toOffsetDateTime(location.getUpdatedAt())
            );
            return new OnlineStatusResult("BUSY", lastKnownLocation);
        }
        return new OnlineStatusResult("ONLINE", null);
    }

    private void validateEntityExists(String entityType, UUID entityId) {
        switch (entityType) {
            case "orders" -> {
                if (orderRepository.findByIdAndDeletedAtIsNull(entityId).isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "ORDER_NOT_FOUND|Khong tim thay don hang.");
                }
            }
            case "drivers" -> {
                if (userRepository.findAdminDriverDetailUser(entityId).isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "DRIVER_NOT_FOUND|Khong tim thay tai xe.");
                }
            }
            case "customers" -> {
                if (userRepository.findAdminCustomerDetailUser(entityId).isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "CUSTOMER_NOT_FOUND|Khong tim thay khach hang.");
                }
            }
            default -> throw invalidAuditFilter("entityType khong hop le");
        }
    }

    private String validateOrderHistoryStatus(String status) {
        String normalizedStatus = status == null ? "ALL" : status;
        if (!ORDER_HISTORY_STATUSES.contains(normalizedStatus)) {
            throw invalidStatusFilter();
        }
        return "ALL".equals(normalizedStatus) ? null : normalizedStatus;
    }

    private void validatePageSize(int page, int size) {
        if (page < 0 || !ALLOWED_PAGE_SIZES.contains(size)) {
            throw invalidPagination();
        }
    }

    private String maskPhone(String raw) {
        if (raw == null) {
            return null;
        }
        int length = raw.length();
        if (length <= 4) {
            return "*".repeat(length);
        }
        return raw.substring(0, 3) + "*".repeat(Math.max(0, length - 6)) + raw.substring(length - 3);
    }

    private String maskReference(String raw) {
        if (raw == null) {
            return null;
        }
        int length = raw.length();
        if (length <= 4) {
            return "*".repeat(length);
        }
        return "***" + raw.substring(length - 4);
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private long safeLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private ResponseStatusException invalidPagination() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_PAGINATION|Tham so phan trang khong hop le.");
    }

    private ResponseStatusException invalidStatusFilter() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_STATUS_FILTER|Bo loc trang thai khong hop le.");
    }

    private ResponseStatusException invalidDateRange() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_DATE_RANGE|Khoang ngay khong hop le.");
    }

    private ResponseStatusException invalidAuditFilter(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_AUDIT_FILTER|Bo loc audit khong hop le.");
    }

    private record OnlineStatusResult(String status, DriverDetailResponse.LastKnownLocation location) {
    }
}
