package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import vn.movehome.backend.dto.admin.CustomerListItem;
import vn.movehome.backend.dto.admin.DashboardOverviewResponse;
import vn.movehome.backend.dto.admin.DriverListItem;
import vn.movehome.backend.dto.admin.DriverPerformanceResponse;
import vn.movehome.backend.dto.admin.KpiResponse;
import vn.movehome.backend.dto.admin.OrderListItem;
import vn.movehome.backend.dto.admin.OrderStatusDistribution;
import vn.movehome.backend.dto.admin.RecentOrderResponse;
import vn.movehome.backend.dto.admin.RevenueByDayResponse;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.OrderStatus;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.OrderRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private OrderRepository orderRepository;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(userRepository, driverProfileRepository, orderRepository);
    }

    // ===== getKpi =====

    @Test
    void getKpiAggregatesAllCountersAndCoalescesNullSums() {
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.CUSTOMER)).thenReturn(10L);
        when(userRepository.countByRoleAndStatusAndDeletedAtIsNull(UserRole.DRIVER, UserStatus.ACTIVE)).thenReturn(5L);
        when(userRepository.countByRoleAndStatusAndDeletedAtIsNull(UserRole.DRIVER, UserStatus.PENDING_APPROVAL)).thenReturn(2L);
        when(orderRepository.countByCreatedAtGreaterThanEqual(any(Instant.class))).thenReturn(3L, 20L);
        when(orderRepository.sumTotalQuoteSince(any(Instant.class))).thenReturn(null);
        when(orderRepository.sumRevenueSince(any(Instant.class))).thenReturn(new BigDecimal("1500000"));
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(1L);
        when(orderRepository.countByStatus(OrderStatus.PENDING_PAYMENT)).thenReturn(2L);
        when(orderRepository.countByStatus(OrderStatus.COMPLETED)).thenReturn(50L);
        when(orderRepository.countByStatus(OrderStatus.DISPUTED)).thenReturn(1L);
        when(orderRepository.countByStatus(OrderStatus.IN_DISPUTE)).thenReturn(1L);

        KpiResponse kpi = service.getKpi();

        assertThat(kpi.totalCustomers()).isEqualTo(10L);
        assertThat(kpi.activeDrivers()).isEqualTo(5L);
        assertThat(kpi.pendingDriverApprovals()).isEqualTo(2L);
        assertThat(kpi.totalOrdersToday()).isEqualTo(3L);
        assertThat(kpi.totalOrdersThisMonth()).isEqualTo(20L);
        assertThat(kpi.totalRevenueThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(kpi.totalCommissionThisMonth()).isEqualByComparingTo(new BigDecimal("1500000"));
        assertThat(kpi.pendingOrders()).isEqualTo(3L);
        assertThat(kpi.completedOrders()).isEqualTo(50L);
        assertThat(kpi.inDisputeOrders()).isEqualTo(2L);
    }

    // ===== getRevenueByDay =====

    @Test
    void getRevenueByDayFillsMissingDaysWithZeroAndMapsExistingRows() {
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        // revenue/commission de dang raw Double/String (khong phai BigDecimal) de test nhanh
        // "else" branch cua toBigDecimal() — DB driver co the tra ve kieu khac nhau tuy dialect.
        Object[] row = new Object[]{today, 4L, 1000000.0d, "300000"};
        when(orderRepository.findDailyCompletedStats(any(Instant.class))).thenReturn(List.<Object[]>of(row));

        RevenueByDayResponse response = service.getRevenueByDay();

        assertThat(response.points()).hasSize(30);
        RevenueByDayResponse.RevenuePoint last = response.points().get(29);
        assertThat(last.date()).isEqualTo(today);
        assertThat(last.orderCount()).isEqualTo(4L);
        assertThat(last.revenue()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(last.commission()).isEqualByComparingTo(new BigDecimal("300000"));

        RevenueByDayResponse.RevenuePoint first = response.points().get(0);
        assertThat(first.orderCount()).isEqualTo(0L);
        assertThat(first.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(first.commission()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getRevenueByDayHandlesNoRowsAtAll() {
        when(orderRepository.findDailyCompletedStats(any(Instant.class))).thenReturn(List.of());

        RevenueByDayResponse response = service.getRevenueByDay();

        assertThat(response.points()).hasSize(30);
        assertThat(response.points()).allSatisfy(p -> {
            assertThat(p.orderCount()).isEqualTo(0L);
            assertThat(p.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(p.commission()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    // ===== getTopDrivers =====

    @Test
    void getTopDriversResolvesDriverNameWhenUserFound() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = DriverProfile.builder()
                .userId(driverId)
                .totalOrdersCompleted(12)
                .totalRevenue(new BigDecimal("5000000"))
                .averageRating(new BigDecimal("4.80"))
                .build();
        when(driverProfileRepository.findTop5ByOrderByTotalRevenueDesc()).thenReturn(List.of(profile));
        User driverUser = User.builder().id(driverId).fullName("Nguyen Van A").build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driverUser));

        DriverPerformanceResponse response = service.getTopDrivers();

        assertThat(response.topDrivers()).hasSize(1);
        DriverPerformanceResponse.DriverStat stat = response.topDrivers().get(0);
        assertThat(stat.driverId()).isEqualTo(driverId);
        assertThat(stat.fullName()).isEqualTo("Nguyen Van A");
        assertThat(stat.totalOrders()).isEqualTo(12L);
        assertThat(stat.totalRevenue()).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(stat.averageRating()).isEqualByComparingTo(new BigDecimal("4.80"));
    }

    @Test
    void getTopDriversFallsBackToDefaultNameWhenUserNotFound() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = DriverProfile.builder()
                .userId(driverId)
                .totalOrdersCompleted(0)
                .totalRevenue(BigDecimal.ZERO)
                .averageRating(new BigDecimal("5.00"))
                .build();
        when(driverProfileRepository.findTop5ByOrderByTotalRevenueDesc()).thenReturn(List.of(profile));
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());

        DriverPerformanceResponse response = service.getTopDrivers();

        assertThat(response.topDrivers().get(0).fullName()).isEqualTo("Driver khong xac dinh");
    }

    @Test
    void getTopDriversReturnsEmptyListWhenNoProfiles() {
        when(driverProfileRepository.findTop5ByOrderByTotalRevenueDesc()).thenReturn(List.of());

        DriverPerformanceResponse response = service.getTopDrivers();

        assertThat(response.topDrivers()).isEmpty();
    }

    // ===== getRecentOrders =====

    @Test
    void getRecentOrdersMapsAllColumnsIncludingNullableDriverName() {
        UUID orderId = UUID.randomUUID();
        // orderId truyen thang kieu UUID (khong phai String) de test nhanh branch
        // "instanceof UUID" cua toUUID() — mot so JDBC driver tra ve UUID that.
        Object[] row = new Object[]{
                orderId, "ORD-001", "COMPLETED", new BigDecimal("2000000"),
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), "Nguyen Van B", null
        };
        when(orderRepository.findRecentOrdersWithNames(10)).thenReturn(List.<Object[]>of(row));

        RecentOrderResponse response = service.getRecentOrders();

        assertThat(response.orders()).hasSize(1);
        RecentOrderResponse.RecentOrderItem item = response.orders().get(0);
        assertThat(item.orderId()).isEqualTo(orderId);
        assertThat(item.orderCode()).isEqualTo("ORD-001");
        assertThat(item.customerName()).isEqualTo("Nguyen Van B");
        assertThat(item.driverName()).isNull();
        assertThat(item.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(item.totalQuote()).isEqualByComparingTo(new BigDecimal("2000000"));
        assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void getRecentOrdersReturnsEmptyWhenNoRows() {
        when(orderRepository.findRecentOrdersWithNames(10)).thenReturn(List.of());

        RecentOrderResponse response = service.getRecentOrders();

        assertThat(response.orders()).isEmpty();
    }

    // ===== getStatusDistribution =====

    @Test
    void getStatusDistributionIncludesEveryEnumValue() {
        when(orderRepository.countByStatus(any(OrderStatus.class))).thenReturn(7L);

        OrderStatusDistribution distribution = service.getStatusDistribution();

        assertThat(distribution.distribution()).hasSize(OrderStatus.values().length);
        assertThat(distribution.distribution().values()).allMatch(v -> v == 7L);
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(distribution.distribution()).containsKey(status);
        }
    }

    // ===== getOverview =====

    @Test
    void getOverviewCombinesAllFiveSubResponses() {
        when(userRepository.countByRoleAndDeletedAtIsNull(any())).thenReturn(0L);
        when(userRepository.countByRoleAndStatusAndDeletedAtIsNull(any(), any())).thenReturn(0L);
        when(orderRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(0L);
        when(orderRepository.sumTotalQuoteSince(any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumRevenueSince(any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.countByStatus(any())).thenReturn(0L);
        when(orderRepository.findDailyCompletedStats(any())).thenReturn(List.of());
        when(driverProfileRepository.findTop5ByOrderByTotalRevenueDesc()).thenReturn(List.of());
        when(orderRepository.findRecentOrdersWithNames(10)).thenReturn(List.of());

        DashboardOverviewResponse overview = service.getOverview();

        assertThat(overview.kpi()).isNotNull();
        assertThat(overview.revenueChart()).isNotNull();
        assertThat(overview.topDrivers()).isNotNull();
        assertThat(overview.recentOrders()).isNotNull();
        assertThat(overview.statusDistribution()).isNotNull();
    }

    // ===== getAllOrders =====

    @Test
    void getAllOrdersWithNullFilterUsesFindAllAndMapsAllNullableColumns() {
        UUID orderId = UUID.randomUUID();
        Object[] row = new Object[]{
                orderId.toString(), "ORD-100", "Khach A", null, "PENDING",
                null, null, new BigDecimal("1000000"), new BigDecimal("0.30"),
                Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")),
                null
        };
        when(orderRepository.findAllOrdersForAdmin()).thenReturn(List.<Object[]>of(row));

        Page<OrderListItem> page = service.getAllOrders(null, PageRequest.of(0, 50));

        assertThat(page.getContent()).hasSize(1);
        OrderListItem item = page.getContent().get(0);
        assertThat(item.id()).isEqualTo(orderId);
        assertThat(item.driverName()).isNull();
        assertThat(item.pickupDistrict()).isNull();
        assertThat(item.dropoffDistrict()).isNull();
        assertThat(item.completedAt()).isNull();
        assertThat(item.status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).findAllOrdersByStatusForAdmin(anyString());
    }

    @Test
    void getAllOrdersWithFilterUsesFindByStatusAndMapsCompletedAt() {
        UUID orderId = UUID.randomUUID();
        Object[] row = new Object[]{
                orderId.toString(), "ORD-200", "Khach B", "Tai xe C", "COMPLETED",
                "Ba Dinh", "Cau Giay", new BigDecimal("3000000"), new BigDecimal("0.30"),
                Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-02-02T00:00:00Z"))
        };
        when(orderRepository.findAllOrdersByStatusForAdmin("COMPLETED")).thenReturn(List.<Object[]>of(row));

        Page<OrderListItem> page = service.getAllOrders(OrderStatus.COMPLETED, PageRequest.of(0, 50));

        assertThat(page.getContent()).hasSize(1);
        OrderListItem item = page.getContent().get(0);
        assertThat(item.driverName()).isEqualTo("Tai xe C");
        assertThat(item.pickupDistrict()).isEqualTo("Ba Dinh");
        assertThat(item.completedAt()).isEqualTo(Instant.parse("2026-02-02T00:00:00Z"));
    }

    @Test
    void getAllOrdersReturnsEmptyPageWhenOffsetBeyondSize() {
        when(orderRepository.findAllOrdersForAdmin()).thenReturn(List.of());

        Page<OrderListItem> page = service.getAllOrders(null, PageRequest.of(5, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    // ===== getAllDrivers =====

    @Test
    void getAllDriversWithNullFilterMapsNullableProfileFieldsToDefaults() {
        UUID userId = UUID.randomUUID();
        Object[] row = new Object[]{
                userId.toString(), "Tai xe D", "driver@movehome.vn", null, "PENDING_DOCUMENTS",
                null, null, null, null, null, null, null,
                Timestamp.from(Instant.parse("2026-01-05T00:00:00Z")), null
        };
        when(userRepository.findAllDriversForAdmin()).thenReturn(List.<Object[]>of(row));

        List<DriverListItem> items = service.getAllDrivers(null);

        assertThat(items).hasSize(1);
        DriverListItem item = items.get(0);
        assertThat(item.userId()).isEqualTo(userId);
        assertThat(item.phone()).isNull();
        assertThat(item.status()).isEqualTo(UserStatus.PENDING_DOCUMENTS);
        assertThat(item.licenseNumber()).isNull();
        assertThat(item.depositAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.totalOrdersCompleted()).isEqualTo(0);
        assertThat(item.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.averageRating()).isNull();
        assertThat(item.approvedAt()).isNull();
        verify(userRepository, never()).findAllDriversByStatusForAdmin(anyString());
    }

    @Test
    void getAllDriversWithFilterMapsFullProfileValues() {
        UUID userId = UUID.randomUUID();
        Object[] row = new Object[]{
                userId.toString(), "Tai xe E", "e@movehome.vn", "0900000000", "ACTIVE",
                "GPLX-123", "30A-12345", "Xe tai", new BigDecimal("3000000"),
                15, new BigDecimal("9000000"), new BigDecimal("4.90"),
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-01-10T00:00:00Z"))
        };
        when(userRepository.findAllDriversByStatusForAdmin("ACTIVE")).thenReturn(List.<Object[]>of(row));

        List<DriverListItem> items = service.getAllDrivers(UserStatus.ACTIVE);

        assertThat(items).hasSize(1);
        DriverListItem item = items.get(0);
        assertThat(item.phone()).isEqualTo("0900000000");
        assertThat(item.licenseNumber()).isEqualTo("GPLX-123");
        assertThat(item.vehiclePlate()).isEqualTo("30A-12345");
        assertThat(item.vehicleType()).isEqualTo("Xe tai");
        assertThat(item.depositAmount()).isEqualByComparingTo(new BigDecimal("3000000"));
        assertThat(item.totalOrdersCompleted()).isEqualTo(15);
        assertThat(item.totalRevenue()).isEqualByComparingTo(new BigDecimal("9000000"));
        assertThat(item.averageRating()).isEqualByComparingTo(new BigDecimal("4.90"));
        assertThat(item.approvedAt()).isEqualTo(Instant.parse("2026-01-10T00:00:00Z"));
    }

    @Test
    void getAllDriversReturnsEmptyListWhenNoRows() {
        when(userRepository.findAllDriversForAdmin()).thenReturn(List.of());

        assertThat(service.getAllDrivers(null)).isEmpty();
    }

    // ===== getAllCustomers =====

    @Test
    void getAllCustomersWithNullFilterMapsFieldsAndForcesLastLoginAtNull() {
        UUID userId = UUID.randomUUID();
        Object[] row = new Object[]{
                userId.toString(), "Khach F", "f@movehome.vn", "0911111111", "ACTIVE",
                true, 3L, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        };
        when(userRepository.findAllCustomersForAdmin()).thenReturn(List.<Object[]>of(row));

        List<CustomerListItem> items = service.getAllCustomers(null);

        assertThat(items).hasSize(1);
        CustomerListItem item = items.get(0);
        assertThat(item.id()).isEqualTo(userId);
        assertThat(item.fullName()).isEqualTo("Khach F");
        assertThat(item.phone()).isEqualTo("0911111111");
        assertThat(item.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(item.emailVerified()).isTrue();
        assertThat(item.totalOrdersPlaced()).isEqualTo(3L);
        assertThat(item.lastLoginAt()).isNull();
        verify(userRepository, never()).findAllCustomersByStatusForAdmin(anyString());
    }

    @Test
    void getAllCustomersWithFilterUsesStatusQueryAndHandlesFalseBoolean() {
        UUID userId = UUID.randomUUID();
        // email_verified truyen dang String "false" (khong phai Boolean) de test nhanh
        // "else" branch cua toBoolean() — mot so driver tra ve boolean dang chuoi.
        Object[] row = new Object[]{
                userId.toString(), "Khach G", "g@movehome.vn", null, "PENDING_VERIFY",
                "false", 0L, Timestamp.from(Instant.parse("2026-01-02T00:00:00Z"))
        };
        when(userRepository.findAllCustomersByStatusForAdmin("PENDING_VERIFY")).thenReturn(List.<Object[]>of(row));

        List<CustomerListItem> items = service.getAllCustomers(UserStatus.PENDING_VERIFY);

        assertThat(items).hasSize(1);
        CustomerListItem item = items.get(0);
        assertThat(item.phone()).isNull();
        assertThat(item.emailVerified()).isFalse();
        assertThat(item.totalOrdersPlaced()).isEqualTo(0L);
    }

    @Test
    void getAllCustomersHandlesNullTotalOrdersPlacedAsZero() {
        UUID userId = UUID.randomUUID();
        // total_orders_placed = null de test nhanh branch "val == null" cua toLong().
        Object[] row = new Object[]{
                userId.toString(), "Khach K", "k@movehome.vn", null, "ACTIVE",
                true, null, Timestamp.from(Instant.parse("2026-01-03T00:00:00Z"))
        };
        when(userRepository.findAllCustomersForAdmin()).thenReturn(List.<Object[]>of(row));

        List<CustomerListItem> items = service.getAllCustomers(null);

        assertThat(items.get(0).totalOrdersPlaced()).isEqualTo(0L);
    }

    @Test
    void getAllCustomersReturnsEmptyListWhenNoRows() {
        when(userRepository.findAllCustomersForAdmin()).thenReturn(List.of());

        assertThat(service.getAllCustomers(null)).isEmpty();
    }

    @Test
    void getAllCustomersHandlesNullEmailVerifiedAsFalse() {
        UUID userId = UUID.randomUUID();
        // email_verified = null de test nhanh branch "val == null" cua toBoolean().
        Object[] row = new Object[]{
                userId.toString(), "Khach L", "l@movehome.vn", null, "ACTIVE",
                null, 1L, Timestamp.from(Instant.parse("2026-01-04T00:00:00Z"))
        };
        when(userRepository.findAllCustomersForAdmin()).thenReturn(List.<Object[]>of(row));

        List<CustomerListItem> items = service.getAllCustomers(null);

        assertThat(items.get(0).emailVerified()).isFalse();
    }

    @Test
    void getAllOrdersHandlesNullTotalQuoteAndCommissionRateAsZero() {
        UUID orderId = UUID.randomUUID();
        // total_quote va commission_rate_snapshot = null de test nhanh branch "val == null" cua toBigDecimal().
        Object[] row = new Object[]{
                orderId.toString(), "ORD-300", "Khach H", null, "PENDING",
                null, null, null, null,
                Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")),
                null
        };
        when(orderRepository.findAllOrdersForAdmin()).thenReturn(List.<Object[]>of(row));

        Page<OrderListItem> page = service.getAllOrders(null, PageRequest.of(0, 50));

        OrderListItem item = page.getContent().get(0);
        assertThat(item.totalQuote()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.commissionRateSnapshot()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ===== toInstant helper via OffsetDateTime and raw String branches =====

    @Test
    void getRecentOrdersHandlesOffsetDateTimeAndRawStringTimestampValues() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        Object[] rowOffset = new Object[]{
                orderId1.toString(), "ORD-300", "PENDING", new BigDecimal("500000"),
                OffsetDateTime.parse("2026-03-01T00:00:00Z"), "Khach H", "Tai xe I"
        };
        Object[] rowStringInstant = new Object[]{
                orderId2.toString(), "ORD-301", "CANCELLED", new BigDecimal("0"),
                "2026-03-02T00:00:00Z", "Khach J", null
        };
        when(orderRepository.findRecentOrdersWithNames(10)).thenReturn(List.<Object[]>of(rowOffset, rowStringInstant));

        RecentOrderResponse response = service.getRecentOrders();

        assertThat(response.orders()).hasSize(2);
        assertThat(response.orders().get(0).createdAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(response.orders().get(1).createdAt()).isEqualTo(Instant.parse("2026-03-02T00:00:00Z"));
        assertThat(response.orders().get(1).driverName()).isNull();
    }
}
