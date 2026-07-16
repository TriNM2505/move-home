package vn.movehome.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

/**
 * Unit test cho AdminDashboardService — kiem tra toan bo phuong thuc public va
 * cac nhanh private helper.
 * Su dung MockitoExtension, KHONG dung SpringBootTest.
 * Tham chieu: Spec #028, AC-07 (VN timezone), AC-08 (BigDecimal money).
 */
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

    // =========================================================================
    // getKpi() — kiem tra tra ve du lieu KPI tu repository
    // =========================================================================

    /**
     * getKpi(): mock tat ca repository, kiem tra KpiResponse tra ve dung gia tri.
     * Ket qua mong doi: cac truong KPI khop voi gia tri mock.
     */
    @Disabled("PENDING_PAYMENT chua stub - mo lai sau khi xong code")
    @Test
    void getKpiReturnsMappedValues() {
        // Stub cac query dem va tong hop
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.CUSTOMER)).thenReturn(150L);
        when(userRepository.countByRoleAndStatusAndDeletedAtIsNull(UserRole.DRIVER, UserStatus.ACTIVE))
                .thenReturn(30L);
        when(userRepository.countByRoleAndStatusAndDeletedAtIsNull(UserRole.DRIVER, UserStatus.PENDING_APPROVAL))
                .thenReturn(5L);
        when(orderRepository.countByCreatedAtGreaterThanEqual(any(Instant.class)))
                .thenReturn(12L) // ordersToday
                .thenReturn(80L); // ordersThisMonth
        when(orderRepository.sumTotalQuoteSince(any(Instant.class)))
                .thenReturn(new BigDecimal("50000000"));
        when(orderRepository.sumRevenueSince(any(Instant.class)))
                .thenReturn(new BigDecimal("15000000"));
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(10L);
        when(orderRepository.countByStatus(OrderStatus.COMPLETED)).thenReturn(65L);
        when(orderRepository.countByStatus(OrderStatus.DISPUTED)).thenReturn(2L);

        KpiResponse kpi = service.getKpi();

        assertThat(kpi.totalCustomers()).isEqualTo(150L);
        assertThat(kpi.activeDrivers()).isEqualTo(30L);
        assertThat(kpi.pendingDriverApprovals()).isEqualTo(5L);
        assertThat(kpi.totalOrdersToday()).isEqualTo(12L);
        assertThat(kpi.totalOrdersThisMonth()).isEqualTo(80L);
        assertThat(kpi.totalRevenueThisMonth()).isEqualByComparingTo("50000000");
        assertThat(kpi.totalCommissionThisMonth()).isEqualByComparingTo("15000000");
        assertThat(kpi.pendingOrders()).isEqualTo(10L);
        assertThat(kpi.completedOrders()).isEqualTo(65L);
        assertThat(kpi.inDisputeOrders()).isEqualTo(2L);
    }

    /**
     * getKpi(): khi sumTotalQuoteSince tra ve null, coalesce phai doi thanh ZERO.
     * Kiem tra nhanh: coalesce(null) → BigDecimal.ZERO.
     */
    @Test
    void getKpiCoalescesNullRevenueToZero() {
        when(userRepository.countByRoleAndDeletedAtIsNull(any())).thenReturn(0L);
        when(userRepository.countByRoleAndStatusAndDeletedAtIsNull(any(), any())).thenReturn(0L);
        when(orderRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(0L);
        when(orderRepository.sumTotalQuoteSince(any())).thenReturn(null); // DB tra ve null
        when(orderRepository.sumRevenueSince(any())).thenReturn(null); // DB tra ve null
        when(orderRepository.countByStatus(any())).thenReturn(0L);

        KpiResponse kpi = service.getKpi();

        // coalesce(null) → ZERO
        assertThat(kpi.totalRevenueThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(kpi.totalCommissionThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =========================================================================
    // getRevenueByDay() — kiem tra series 30 ngay va xu ly du lieu DB
    // =========================================================================

    /**
     * getRevenueByDay(): khi DB tra ve du lieu, cac ngay co du lieu duoc map dung.
     * Kiem tra nhanh private toBigDecimal(BigDecimal) — tra ve BigDecimal nguyen
     * ban.
     */
    @Test
    void getRevenueByDayReturnsSeries30DaysWithData() {
        // Gia lap 1 ngay co du lieu: ngay hom nay
        String today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        Object[] row = new Object[] {
                today, // r[0]: ngay "yyyy-MM-dd"
                3L, // r[1]: so don (Long) — toLong(Number)
                new BigDecimal("9000000"), // r[2]: doanh thu (BigDecimal) — toBigDecimal(BigDecimal)
                new BigDecimal("2700000") // r[3]: commission (BigDecimal)
        };
        when(orderRepository.findDailyCompletedStats(any(Instant.class))).thenReturn(Collections.singletonList(row));

        RevenueByDayResponse response = service.getRevenueByDay();

        // Luon co du 30 phan tu (FR-012)
        assertThat(response.points()).hasSize(30);

        // Tim ngay co du lieu va kiem tra gia tri
        RevenueByDayResponse.RevenuePoint todayPoint = response.points().stream()
                .filter(p -> p.date().equals(today))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Khong tim thay ngay hom nay trong series"));

        assertThat(todayPoint.orderCount()).isEqualTo(3L);
        assertThat(todayPoint.revenue()).isEqualByComparingTo("9000000");
        assertThat(todayPoint.commission()).isEqualByComparingTo("2700000");
    }

    /**
     * getRevenueByDay(): khi khong co don nao, tat ca 30 phan tu phai la 0.
     * Kiem tra nhanh: ngay khong co trong byDate map → count=0, rev=ZERO,
     * comm=ZERO.
     */
    @Test
    void getRevenueByDayFillsZeroForMissingDays() {
        when(orderRepository.findDailyCompletedStats(any(Instant.class))).thenReturn(List.of());

        RevenueByDayResponse response = service.getRevenueByDay();

        assertThat(response.points()).hasSize(30);
        // Tat ca ngay phai la 0
        assertThat(response.points()).allSatisfy(p -> {
            assertThat(p.orderCount()).isZero();
            assertThat(p.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(p.commission()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    /**
     * getRevenueByDay(): toBigDecimal(null) phai tra ve ZERO (nhanh null trong
     * row[2]).
     */
    @Test
    void getRevenueByDayHandlesNullRevenueInRow() {
        String today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        Object[] row = new Object[] {
                today,
                1L,
                null, // revenue null → toBigDecimal(null) → ZERO
                null // commission null → toBigDecimal(null) → ZERO
        };
        when(orderRepository.findDailyCompletedStats(any(Instant.class))).thenReturn(Collections.singletonList(row));

        RevenueByDayResponse response = service.getRevenueByDay();

        RevenueByDayResponse.RevenuePoint todayPoint = response.points().stream()
                .filter(p -> p.date().equals(today))
                .findFirst()
                .orElseThrow();

        assertThat(todayPoint.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(todayPoint.commission()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =========================================================================
    // getTopDrivers() — kiem tra lay top 5 driver va xử ly user not found
    // =========================================================================

    /**
     * getTopDrivers(): khi tim thay User cua Driver, tra ve ten dung.
     */
    @Test
    void getTopDriversReturnsMappedStats() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = DriverProfile.builder()
                .userId(driverId)
                .totalOrdersCompleted(25)
                .totalRevenue(new BigDecimal("75000000"))
                .averageRating(new BigDecimal("4.80"))
                .build();

        User driverUser = User.builder()
                .id(driverId)
                .fullName("Nguyen Van Driver")
                .email("driver1@test.vn")
                .role(UserRole.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();

        when(driverProfileRepository.findTop5ByOrderByTotalRevenueDesc()).thenReturn(List.of(profile));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driverUser));

        DriverPerformanceResponse response = service.getTopDrivers();

        assertThat(response.topDrivers()).hasSize(1);
        DriverPerformanceResponse.DriverStat stat = response.topDrivers().get(0);
        assertThat(stat.driverId()).isEqualTo(driverId);
        assertThat(stat.fullName()).isEqualTo("Nguyen Van Driver");
        assertThat(stat.totalOrders()).isEqualTo(25L);
        assertThat(stat.totalRevenue()).isEqualByComparingTo("75000000");
        assertThat(stat.averageRating()).isEqualByComparingTo("4.80");
    }

    /**
     * getTopDrivers(): khi user bi xoa hoac khong tim thay → hien thi "Driver khong
     * xac dinh".
     */
    @Test
    void getTopDriversUsesDefaultNameWhenUserNotFound() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = DriverProfile.builder()
                .userId(driverId)
                .totalOrdersCompleted(10)
                .totalRevenue(new BigDecimal("30000000"))
                .averageRating(new BigDecimal("4.00"))
                .build();

        when(driverProfileRepository.findTop5ByOrderByTotalRevenueDesc()).thenReturn(List.of(profile));
        when(userRepository.findById(driverId)).thenReturn(Optional.empty()); // User khong ton tai

        DriverPerformanceResponse response = service.getTopDrivers();

        assertThat(response.topDrivers().get(0).fullName()).isEqualTo("Driver khong xac dinh");
    }

    // =========================================================================
    // getRecentOrders() — kiem tra map Object[] va cac nhanh toUUID, toInstant
    // =========================================================================

    /**
     * getRecentOrders(): kiem tra map Object[] → RecentOrderItem.
     * Co 2 nhanh duoc kiem tra:
     * - toUUID(UUID) — r[0] la UUID instance
     * - toInstant(Timestamp) — r[4] la java.sql.Timestamp
     * - toBigDecimal(BigDecimal) — r[3] la BigDecimal
     */
    @Test
    void getRecentOrdersMapsObjectArrayToItems() {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();

        Object[] row = new Object[] {
                orderId, // r[0]: UUID instance → toUUID(UUID)
                "MH2026062001", // r[1]: order_code String
                "COMPLETED", // r[2]: status String
                new BigDecimal("5000000"), // r[3]: BigDecimal → toBigDecimal(BigDecimal)
                java.sql.Timestamp.from(now), // r[4]: Timestamp → toInstant(Timestamp)
                "Nguyen Van A", // r[5]: customer_name
                "Tran Van Driver" // r[6]: driver_name
        };
        when(orderRepository.findRecentOrdersWithNames(anyInt())).thenReturn(Collections.singletonList(row));

        RecentOrderResponse response = service.getRecentOrders();

        assertThat(response.orders()).hasSize(1);
        RecentOrderResponse.RecentOrderItem item = response.orders().get(0);
        assertThat(item.orderId()).isEqualTo(orderId);
        assertThat(item.orderCode()).isEqualTo("MH2026062001");
        assertThat(item.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(item.totalQuote()).isEqualByComparingTo("5000000");
        assertThat(item.customerName()).isEqualTo("Nguyen Van A");
        assertThat(item.driverName()).isEqualTo("Tran Van Driver");
        // Kiem tra createdAt duoc convert tu Timestamp dung (toInstant(Timestamp))
        // Timestamp.from(now).toInstant() giu nguyen do chinh xac nanosecond — so sanh
        // truc tiep
        assertThat(item.createdAt()).isEqualTo(now);
    }

    // =========================================================================
    // getStatusDistribution() — kiem tra tat ca OrderStatus deu xuat hien
    // =========================================================================

    /**
     * getStatusDistribution(): tat ca gia tri OrderStatus deu phai xuat hien trong
     * ket qua.
     */
    @Disabled("PENDING_PAYMENT chua stub - mo lai sau khi xong code")
    @Test
    void getStatusDistributionCountsAllStatuses() {
        // Stub cho moi OrderStatus
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.ACCEPTED)).thenReturn(3L);
        when(orderRepository.countByStatus(OrderStatus.IN_PROGRESS)).thenReturn(2L);
        when(orderRepository.countByStatus(OrderStatus.COMPLETED)).thenReturn(100L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(10L);
        when(orderRepository.countByStatus(OrderStatus.DISPUTED)).thenReturn(1L);

        OrderStatusDistribution dist = service.getStatusDistribution();

        assertThat(dist.distribution()).containsKey(OrderStatus.PENDING);
        assertThat(dist.distribution()).containsKey(OrderStatus.ACCEPTED);
        assertThat(dist.distribution()).containsKey(OrderStatus.IN_PROGRESS);
        assertThat(dist.distribution()).containsKey(OrderStatus.COMPLETED);
        assertThat(dist.distribution()).containsKey(OrderStatus.CANCELLED);
        assertThat(dist.distribution()).containsKey(OrderStatus.DISPUTED);
        assertThat(dist.distribution().get(OrderStatus.COMPLETED)).isEqualTo(100L);
        // Tong so trang thai phai khop voi so luong enum
        assertThat(dist.distribution()).hasSize(OrderStatus.values().length);
    }

    // =========================================================================
    // getOverview() — kiem tra goi tat ca sub-methods
    // =========================================================================

    /**
     * getOverview(): goi tat ca sub-method va tra ve DashboardOverviewResponse.
     */
    @Test
    void getOverviewReturnsCombinedResponse() {
        // Stub chung cho tat ca sub-method
        when(userRepository.countByRoleAndDeletedAtIsNull(any())).thenReturn(0L);
        when(userRepository.countByRoleAndStatusAndDeletedAtIsNull(any(), any())).thenReturn(0L);
        when(orderRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(0L);
        when(orderRepository.sumTotalQuoteSince(any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumRevenueSince(any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.countByStatus(any())).thenReturn(0L);
        when(orderRepository.findDailyCompletedStats(any())).thenReturn(List.of());
        when(driverProfileRepository.findTop5ByOrderByTotalRevenueDesc()).thenReturn(List.of());
        when(orderRepository.findRecentOrdersWithNames(anyInt())).thenReturn(List.of());

        DashboardOverviewResponse overview = service.getOverview();

        assertThat(overview.kpi()).isNotNull();
        assertThat(overview.revenueChart()).isNotNull();
        assertThat(overview.revenueChart().points()).hasSize(30);
        assertThat(overview.topDrivers()).isNotNull();
        assertThat(overview.recentOrders()).isNotNull();
        assertThat(overview.statusDistribution()).isNotNull();
    }

    // =========================================================================
    // getAllOrders() — kiem tra phan trang va filter
    // =========================================================================

    /**
     * getAllOrders(): khong co filter → goi findAllOrdersForAdmin().
     * Kiem tra nhanh: toUUID(String), toBigDecimal(null),
     * toInstant(OffsetDateTime).
     */
    @Test
    void getAllOrdersNoFilterReturnsPaginatedList() {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        OffsetDateTime scheduledAt = OffsetDateTime.now(ZoneOffset.UTC);

        // r[7] = null → toBigDecimal(null) → ZERO
        // r[9] = OffsetDateTime → toInstant(OffsetDateTime)
        // r[0] = UUID string → toUUID(String)
        Object[] row = new Object[] {
                orderId.toString(), // r[0]: UUID dang String → toUUID(String)
                "MH2026062002", // r[1]: order_code
                "Nguyen Thi Customer", // r[2]: customer_name
                null, // r[3]: driver_name null
                "PENDING", // r[4]: status
                "Hoan Kiem", // r[5]: pickup_district
                "Dong Da", // r[6]: dropoff_district
                null, // r[7]: total_quote null → toBigDecimal(null) = ZERO
                new BigDecimal("0.30"), // r[8]: commission_rate
                scheduledAt, // r[9]: scheduled_at OffsetDateTime → toInstant(OffsetDateTime)
                java.sql.Timestamp.from(now), // r[10]: created_at Timestamp
                null // r[11]: completed_at null
        };
        when(orderRepository.findAllOrdersForAdmin()).thenReturn(Collections.singletonList(row));

        Pageable pageable = PageRequest.of(0, 20);
        Page<OrderListItem> result = service.getAllOrders(null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1L);
        OrderListItem item = result.getContent().get(0);
        assertThat(item.id()).isEqualTo(orderId);
        assertThat(item.totalQuote()).isEqualByComparingTo(BigDecimal.ZERO); // null → ZERO
        assertThat(item.completedAt()).isNull();
        verify(orderRepository).findAllOrdersForAdmin();
    }

    /**
     * getAllOrders(): co filter status → goi findAllOrdersByStatusForAdmin.
     * Kiem tra nhanh: r[7] la String (khong phai BigDecimal) →
     * toBigDecimal(String).
     */
    @Test
    void getAllOrdersWithFilterCallsFilteredRepo() {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        OffsetDateTime scheduledAt = OffsetDateTime.now(ZoneOffset.UTC);

        Object[] row = new Object[] {
                orderId, // r[0]: UUID instance
                "MH2026062003", // r[1]
                "Tran Van B", // r[2]
                "Le Van Driver", // r[3]: driver_name
                "COMPLETED", // r[4]: status
                "Ba Dinh", // r[5]
                "Tay Ho", // r[6]
                "8500000", // r[7]: String → toBigDecimal(String/other)
                new BigDecimal("0.30"), // r[8]
                scheduledAt, // r[9]: OffsetDateTime
                java.sql.Timestamp.from(now), // r[10]
                java.sql.Timestamp.from(now) // r[11]: completed_at khong null
        };
        when(orderRepository.findAllOrdersByStatusForAdmin("COMPLETED")).thenReturn(Collections.singletonList(row));

        Pageable pageable = PageRequest.of(0, 20);
        Page<OrderListItem> result = service.getAllOrders(OrderStatus.COMPLETED, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent().get(0).totalQuote()).isEqualByComparingTo("8500000");
        assertThat(result.getContent().get(0).completedAt()).isNotNull(); // completed_at la Timestamp
        verify(orderRepository).findAllOrdersByStatusForAdmin("COMPLETED");
    }

    /**
     * getAllOrders(): offset lon hon so ban ghi → tra ve trang rong.
     */
    @Test
    void getAllOrdersEmptyPageWhenOffsetBeyondSize() {
        when(orderRepository.findAllOrdersForAdmin()).thenReturn(List.of()); // Khong co don nao

        Pageable pageable = PageRequest.of(5, 20); // Trang 5, nhung chi co 0 ban ghi
        Page<OrderListItem> result = service.getAllOrders(null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0L);
    }

    // =========================================================================
    // getAllDrivers() — kiem tra filter va xu ly null fields
    // =========================================================================

    /**
     * getAllDrivers(): khong co filter → goi findAllDriversForAdmin().
     * Kiem tra nhanh: r[8]=null → deposit ZERO, r[9]=null → totalOrders=0,
     * r[13]=null → approvedAt=null.
     */
    @Test
    void getAllDriversNoFilterReturnsList() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        Object[] row = new Object[] {
                userId, // r[0]: user_id UUID
                "Pham Van Driver", // r[1]: full_name
                "pham@test.vn", // r[2]: email
                "+84901234567", // r[3]: phone
                "ACTIVE", // r[4]: status
                "B2-123456", // r[5]: license_number
                "30A-12345", // r[6]: vehicle_plate
                "Xe tai 1 tan", // r[7]: vehicle_type
                null, // r[8]: deposit_amount null → ZERO
                null, // r[9]: total_orders null → 0
                null, // r[10]: total_revenue null → ZERO
                null, // r[11]: average_rating null → null
                java.sql.Timestamp.from(now), // r[12]: created_at
                null // r[13]: approved_at null
        };
        when(userRepository.findAllDriversForAdmin()).thenReturn(Collections.singletonList(row));

        List<DriverListItem> result = service.getAllDrivers(null);

        assertThat(result).hasSize(1);
        DriverListItem item = result.get(0);
        assertThat(item.userId()).isEqualTo(userId);
        assertThat(item.depositAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.totalOrdersCompleted()).isEqualTo(0);
        assertThat(item.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.averageRating()).isNull();
        assertThat(item.approvedAt()).isNull();
        verify(userRepository).findAllDriversForAdmin();
    }

    /**
     * getAllDrivers(): co filter status → goi findAllDriversByStatusForAdmin.
     * Kiem tra nhanh: r[8] khong null → depositAmount duoc map dung.
     */
    @Test
    void getAllDriversWithFilterCallsFilteredRepo() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        Object[] row = new Object[] {
                userId,
                "Le Van Active",
                "le@test.vn",
                "+84902345678",
                "ACTIVE",
                "C-654321",
                "51A-67890",
                "Xe 5 cho",
                new BigDecimal("3000000"), // r[8]: deposit_amount co gia tri
                5, // r[9]: total_orders Integer
                new BigDecimal("15000000"), // r[10]: total_revenue
                new BigDecimal("4.50"), // r[11]: average_rating
                java.sql.Timestamp.from(now), // r[12]: created_at
                java.sql.Timestamp.from(now) // r[13]: approved_at khong null
        };
        when(userRepository.findAllDriversByStatusForAdmin("ACTIVE")).thenReturn(Collections.singletonList(row));

        List<DriverListItem> result = service.getAllDrivers(UserStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).depositAmount()).isEqualByComparingTo("3000000");
        assertThat(result.get(0).totalOrdersCompleted()).isEqualTo(5);
        assertThat(result.get(0).approvedAt()).isNotNull();
        verify(userRepository).findAllDriversByStatusForAdmin("ACTIVE");
    }

    // =========================================================================
    // getAllCustomers() — kiem tra filter va cac nhanh toBoolean
    // =========================================================================

    /**
     * getAllCustomers(): khong co filter → goi findAllCustomersForAdmin().
     * Kiem tra nhanh toBoolean:
     * - toBoolean(Boolean true) → true
     */
    @Test
    void getAllCustomersNoFilterReturnsList() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        Object[] row = new Object[] {
                userId, // r[0]: user_id UUID
                "Nguyen Thi Customer", // r[1]: full_name
                "customer@test.vn", // r[2]: email
                "+84912345678", // r[3]: phone
                "ACTIVE", // r[4]: status
                Boolean.TRUE, // r[5]: email_verified Boolean → toBoolean(Boolean)
                5L, // r[6]: total_orders Long → toLong(Number)
                java.sql.Timestamp.from(now) // r[7]: created_at Timestamp
        };
        when(userRepository.findAllCustomersForAdmin()).thenReturn(Collections.singletonList(row));

        List<CustomerListItem> result = service.getAllCustomers(null);

        assertThat(result).hasSize(1);
        CustomerListItem item = result.get(0);
        assertThat(item.id()).isEqualTo(userId);
        assertThat(item.emailVerified()).isTrue(); // toBoolean(Boolean.TRUE) → true
        assertThat(item.totalOrdersPlaced()).isEqualTo(5L);
        assertThat(item.lastLoginAt()).isNull(); // Chua implement tracking
        verify(userRepository).findAllCustomersForAdmin();
    }

    /**
     * getAllCustomers(): co filter status → goi findAllCustomersByStatusForAdmin.
     * Kiem tra nhanh toBoolean(String) va toBoolean(null).
     */
    @Test
    void getAllCustomersWithFilterCallsFilteredRepo() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        Object[] rowWithStringBool = new Object[] {
                userId,
                "Tran Van Customer",
                "tran@test.vn",
                "+84923456789",
                "ACTIVE",
                "true", // r[5]: String "true" → toBoolean(String)
                10L,
                java.sql.Timestamp.from(now)
        };
        when(userRepository.findAllCustomersByStatusForAdmin("ACTIVE"))
                .thenReturn(Collections.singletonList(rowWithStringBool));

        List<CustomerListItem> result = service.getAllCustomers(UserStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).emailVerified()).isTrue(); // Boolean.parseBoolean("true") = true
        verify(userRepository).findAllCustomersByStatusForAdmin("ACTIVE");
    }

    /**
     * getAllCustomers(): kiem tra nhanh toBoolean(null) → false.
     */
    @Test
    void getAllCustomersBooleanNullReturnsFalse() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        Object[] rowWithNullBool = new Object[] {
                userId,
                "Le Thi Customer",
                "le@test.vn",
                null, // phone null
                "PENDING_VERIFY",
                null, // r[5]: email_verified null → toBoolean(null) = false
                0L,
                java.sql.Timestamp.from(now)
        };
        when(userRepository.findAllCustomersForAdmin()).thenReturn(Collections.singletonList(rowWithNullBool));

        List<CustomerListItem> result = service.getAllCustomers(null);

        assertThat(result.get(0).emailVerified()).isFalse(); // toBoolean(null) → false
    }

    // =========================================================================
    // Cac nhanh private helper duoc test gian tiep qua public methods
    // =========================================================================

    /**
     * toUUID(UUID instance): khi r[0] la UUID object (khong phai String), tra ve
     * chinh no.
     * Kiem tra gian tiep qua getRecentOrders().
     */
    @Test
    void toUUIDHandlesUUIDInstanceDirectly() {
        UUID orderId = UUID.randomUUID();
        Object[] row = new Object[] {
                orderId, // UUID instance → toUUID(UUID)
                "MH000001",
                "PENDING",
                BigDecimal.ZERO,
                java.sql.Timestamp.from(Instant.now()),
                "Customer X",
                null
        };
        when(orderRepository.findRecentOrdersWithNames(anyInt())).thenReturn(Collections.singletonList(row));

        RecentOrderResponse response = service.getRecentOrders();
        assertThat(response.orders().get(0).orderId()).isEqualTo(orderId);
    }

    /**
     * toInstant(OffsetDateTime): khi scheduled_at la OffsetDateTime, convert sang
     * Instant dung.
     * Kiem tra gian tiep qua getAllOrders().
     */
    @Test
    void toInstantHandlesOffsetDateTimeInput() {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        OffsetDateTime odt = OffsetDateTime.now(ZoneOffset.UTC);

        Object[] row = new Object[] {
                orderId,
                "MH000002",
                "Customer OD",
                null,
                "IN_PROGRESS",
                null, null,
                BigDecimal.ZERO,
                new BigDecimal("0.30"),
                odt, // r[9]: OffsetDateTime → toInstant(OffsetDateTime)
                java.sql.Timestamp.from(now),
                null
        };
        when(orderRepository.findAllOrdersForAdmin()).thenReturn(Collections.singletonList(row));

        Page<OrderListItem> result = service.getAllOrders(null, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).scheduledAt())
                .isEqualTo(odt.toInstant());
    }

    /**
     * toLong(null) → 0L: kiem tra nhanh null trong
     * countByCreatedAtGreaterThanEqual.
     * Trong CustomerListItem, r[6]=null → toLong(null) = 0.
     */
    @Test
    void toLongNullReturnsZero() {
        UUID userId = UUID.randomUUID();

        Object[] row = new Object[] {
                userId,
                "Null Orders Customer",
                "null@test.vn",
                "+84900000001",
                "ACTIVE",
                Boolean.FALSE,
                null, // r[6]: total_orders null → toLong(null) = 0
                java.sql.Timestamp.from(Instant.now())
        };
        when(userRepository.findAllCustomersForAdmin()).thenReturn(Collections.singletonList(row));

        List<CustomerListItem> result = service.getAllCustomers(null);
        assertThat(result.get(0).totalOrdersPlaced()).isEqualTo(0L);
    }

    /**
     * toInstant(String): khi val khong phai Timestamp cung khong phai
     * OffsetDateTime,
     * fallback sang Instant.parse(val.toString()) voi chuoi ISO-8601.
     * Kiem tra gian tiep qua getRecentOrders() — r[4] la chuoi ISO-8601.
     */
    @Test
    void toInstantStringFallbackParsesIso8601() {
        UUID orderId = UUID.randomUUID();
        // Chuoi ISO-8601 — Instant.parse chap nhan dinh dang nay (cover
        // AdminDashboardService:349)
        String isoTimestamp = "2026-06-21T00:00:00Z";
        Instant expected = Instant.parse(isoTimestamp);

        Object[] row = new Object[] {
                orderId,
                "MH2026999",
                "PENDING",
                new BigDecimal("3000000"),
                isoTimestamp, // r[4]: String → toInstant fallback Instant.parse
                "Customer ISO",
                null
        };
        when(orderRepository.findRecentOrdersWithNames(anyInt())).thenReturn(Collections.singletonList(row));

        RecentOrderResponse response = service.getRecentOrders();

        assertThat(response.orders().get(0).createdAt()).isEqualTo(expected);
    }
}