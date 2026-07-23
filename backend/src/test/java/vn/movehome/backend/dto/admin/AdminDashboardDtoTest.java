package vn.movehome.backend.dto.admin;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.entity.OrderStatus;
import vn.movehome.backend.entity.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cac DTO trong dto/admin la record thuan tuy (khong logic) — test nay chi
 * dam bao constructor + toan bo accessor + equals/hashCode/toString duoc goi
 * it nhat 1 lan de dat coverage (Phase 3C DTOs, Spec #028).
 */
class AdminDashboardDtoTest {

    @Test
    void kpiResponseExposesAllAccessors() {
        KpiResponse kpi = new KpiResponse(1L, 2L, 3L, 4L, 5L,
                new BigDecimal("1000000"), new BigDecimal("300000"), 6L, 7L, 8L);

        assertThat(kpi.totalCustomers()).isEqualTo(1L);
        assertThat(kpi.activeDrivers()).isEqualTo(2L);
        assertThat(kpi.pendingDriverApprovals()).isEqualTo(3L);
        assertThat(kpi.totalOrdersToday()).isEqualTo(4L);
        assertThat(kpi.totalOrdersThisMonth()).isEqualTo(5L);
        assertThat(kpi.totalRevenueThisMonth()).isEqualByComparingTo("1000000");
        assertThat(kpi.totalCommissionThisMonth()).isEqualByComparingTo("300000");
        assertThat(kpi.pendingOrders()).isEqualTo(6L);
        assertThat(kpi.completedOrders()).isEqualTo(7L);
        assertThat(kpi.inDisputeOrders()).isEqualTo(8L);
        assertThat(kpi).isEqualTo(kpi);
        assertThat(kpi.hashCode()).isEqualTo(kpi.hashCode());
        assertThat(kpi.toString()).contains("totalCustomers");
    }

    @Test
    void revenueByDayResponseAndRevenuePointExposeAllAccessors() {
        RevenueByDayResponse.RevenuePoint point = new RevenueByDayResponse.RevenuePoint(
                "2026-01-01", new BigDecimal("500000"), new BigDecimal("150000"), 3L);
        RevenueByDayResponse response = new RevenueByDayResponse(List.of(point));

        assertThat(point.date()).isEqualTo("2026-01-01");
        assertThat(point.revenue()).isEqualByComparingTo("500000");
        assertThat(point.commission()).isEqualByComparingTo("150000");
        assertThat(point.orderCount()).isEqualTo(3L);
        assertThat(response.points()).containsExactly(point);
        assertThat(point).isEqualTo(point);
        assertThat(point.hashCode()).isEqualTo(point.hashCode());
        assertThat(point.toString()).contains("2026-01-01");
        assertThat(response.toString()).contains("points");
    }

    @Test
    void orderStatusDistributionExposesDistribution() {
        Map<OrderStatus, Long> map = Map.of(OrderStatus.PENDING, 5L, OrderStatus.COMPLETED, 10L);
        OrderStatusDistribution distribution = new OrderStatusDistribution(map);

        assertThat(distribution.distribution()).isEqualTo(map);
        assertThat(distribution).isEqualTo(distribution);
        assertThat(distribution.hashCode()).isEqualTo(distribution.hashCode());
        assertThat(distribution.toString()).contains("distribution");
    }

    @Test
    void recentOrderResponseAndItemExposeAllAccessors() {
        UUID orderId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        RecentOrderResponse.RecentOrderItem item = new RecentOrderResponse.RecentOrderItem(
                orderId, "ORD-001", "Khach A", "Tai xe B", OrderStatus.CONFIRMED,
                new BigDecimal("1000000"), createdAt);
        RecentOrderResponse response = new RecentOrderResponse(List.of(item));

        assertThat(item.orderId()).isEqualTo(orderId);
        assertThat(item.orderCode()).isEqualTo("ORD-001");
        assertThat(item.customerName()).isEqualTo("Khach A");
        assertThat(item.driverName()).isEqualTo("Tai xe B");
        assertThat(item.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(item.totalQuote()).isEqualByComparingTo("1000000");
        assertThat(item.createdAt()).isEqualTo(createdAt);
        assertThat(response.orders()).containsExactly(item);
        assertThat(item).isEqualTo(item);
        assertThat(item.hashCode()).isEqualTo(item.hashCode());
        assertThat(item.toString()).contains("ORD-001");
        assertThat(response.toString()).contains("orders");
    }

    @Test
    void driverPerformanceResponseAndDriverStatExposeAllAccessors() {
        UUID driverId = UUID.randomUUID();
        DriverPerformanceResponse.DriverStat stat = new DriverPerformanceResponse.DriverStat(
                driverId, "Tai xe C", 12L, new BigDecimal("9000000"), new BigDecimal("4.75"));
        DriverPerformanceResponse response = new DriverPerformanceResponse(List.of(stat));

        assertThat(stat.driverId()).isEqualTo(driverId);
        assertThat(stat.fullName()).isEqualTo("Tai xe C");
        assertThat(stat.totalOrders()).isEqualTo(12L);
        assertThat(stat.totalRevenue()).isEqualByComparingTo("9000000");
        assertThat(stat.averageRating()).isEqualByComparingTo("4.75");
        assertThat(response.topDrivers()).containsExactly(stat);
        assertThat(stat).isEqualTo(stat);
        assertThat(stat.hashCode()).isEqualTo(stat.hashCode());
        assertThat(stat.toString()).contains("Tai xe C");
        assertThat(response.toString()).contains("topDrivers");
    }

    @Test
    void orderListItemExposesAllAccessorsIncludingNullableFields() {
        UUID id = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant createdAt = Instant.parse("2026-01-01T01:00:00Z");
        OrderListItem item = new OrderListItem(
                id, "ORD-100", "Khach D", null, OrderStatus.PENDING,
                null, null, new BigDecimal("2000000"), new BigDecimal("0.30"),
                scheduledAt, createdAt, null);

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.orderCode()).isEqualTo("ORD-100");
        assertThat(item.customerName()).isEqualTo("Khach D");
        assertThat(item.driverName()).isNull();
        assertThat(item.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(item.pickupDistrict()).isNull();
        assertThat(item.dropoffDistrict()).isNull();
        assertThat(item.totalQuote()).isEqualByComparingTo("2000000");
        assertThat(item.commissionRateSnapshot()).isEqualByComparingTo("0.30");
        assertThat(item.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(item.createdAt()).isEqualTo(createdAt);
        assertThat(item.completedAt()).isNull();
        assertThat(item).isEqualTo(item);
        assertThat(item.hashCode()).isEqualTo(item.hashCode());
        assertThat(item.toString()).contains("ORD-100");
    }

    @Test
    void orderListItemWithCompletedAtPopulated() {
        Instant completedAt = Instant.parse("2026-01-02T00:00:00Z");
        OrderListItem item = new OrderListItem(
                UUID.randomUUID(), "ORD-101", "Khach E", "Tai xe F", OrderStatus.COMPLETED,
                "Ba Dinh", "Cau Giay", new BigDecimal("3000000"), new BigDecimal("0.30"),
                Instant.now(), Instant.now(), completedAt);

        assertThat(item.driverName()).isEqualTo("Tai xe F");
        assertThat(item.pickupDistrict()).isEqualTo("Ba Dinh");
        assertThat(item.dropoffDistrict()).isEqualTo("Cau Giay");
        assertThat(item.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void driverListItemExposesAllAccessorsWithFullValues() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant approvedAt = Instant.parse("2026-01-05T00:00:00Z");
        DriverListItem item = new DriverListItem(
                userId, "Tai xe G", "g@movehome.vn", "0900000000", UserStatus.ACTIVE,
                "GPLX-1", "30A-11111", "Xe tai", new BigDecimal("3000000"),
                20, new BigDecimal("10000000"), new BigDecimal("4.80"),
                createdAt, approvedAt);

        assertThat(item.userId()).isEqualTo(userId);
        assertThat(item.fullName()).isEqualTo("Tai xe G");
        assertThat(item.email()).isEqualTo("g@movehome.vn");
        assertThat(item.phone()).isEqualTo("0900000000");
        assertThat(item.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(item.licenseNumber()).isEqualTo("GPLX-1");
        assertThat(item.vehiclePlate()).isEqualTo("30A-11111");
        assertThat(item.vehicleType()).isEqualTo("Xe tai");
        assertThat(item.depositAmount()).isEqualByComparingTo("3000000");
        assertThat(item.totalOrdersCompleted()).isEqualTo(20);
        assertThat(item.totalRevenue()).isEqualByComparingTo("10000000");
        assertThat(item.averageRating()).isEqualByComparingTo("4.80");
        assertThat(item.createdAt()).isEqualTo(createdAt);
        assertThat(item.approvedAt()).isEqualTo(approvedAt);
        assertThat(item).isEqualTo(item);
        assertThat(item.hashCode()).isEqualTo(item.hashCode());
        assertThat(item.toString()).contains("Tai xe G");
    }

    @Test
    void driverListItemWithNullableProfileFields() {
        DriverListItem item = new DriverListItem(
                UUID.randomUUID(), "Tai xe H", "h@movehome.vn", null, UserStatus.PENDING_DOCUMENTS,
                null, null, null, BigDecimal.ZERO, 0, BigDecimal.ZERO, null,
                Instant.now(), null);

        assertThat(item.phone()).isNull();
        assertThat(item.licenseNumber()).isNull();
        assertThat(item.vehiclePlate()).isNull();
        assertThat(item.vehicleType()).isNull();
        assertThat(item.averageRating()).isNull();
        assertThat(item.approvedAt()).isNull();
    }

    @Test
    void customerListItemExposesAllAccessors() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        CustomerListItem item = new CustomerListItem(
                id, "Khach I", "i@movehome.vn", "0911111111", UserStatus.ACTIVE,
                true, 5L, createdAt, null);

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.fullName()).isEqualTo("Khach I");
        assertThat(item.email()).isEqualTo("i@movehome.vn");
        assertThat(item.phone()).isEqualTo("0911111111");
        assertThat(item.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(item.emailVerified()).isTrue();
        assertThat(item.totalOrdersPlaced()).isEqualTo(5L);
        assertThat(item.createdAt()).isEqualTo(createdAt);
        assertThat(item.lastLoginAt()).isNull();
        assertThat(item).isEqualTo(item);
        assertThat(item.hashCode()).isEqualTo(item.hashCode());
        assertThat(item.toString()).contains("Khach I");
    }

    @Test
    void customerListItemWithUnverifiedEmailAndNoPhone() {
        CustomerListItem item = new CustomerListItem(
                UUID.randomUUID(), "Khach J", "j@movehome.vn", null, UserStatus.PENDING_VERIFY,
                false, 0L, Instant.now(), null);

        assertThat(item.phone()).isNull();
        assertThat(item.emailVerified()).isFalse();
        assertThat(item.totalOrdersPlaced()).isEqualTo(0L);
    }

    @Test
    void dashboardOverviewResponseExposesAllSubResponses() {
        KpiResponse kpi = new KpiResponse(0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0);
        RevenueByDayResponse revenue = new RevenueByDayResponse(List.of());
        DriverPerformanceResponse topDrivers = new DriverPerformanceResponse(List.of());
        RecentOrderResponse recentOrders = new RecentOrderResponse(List.of());
        OrderStatusDistribution distribution = new OrderStatusDistribution(Map.of());

        DashboardOverviewResponse overview = new DashboardOverviewResponse(
                kpi, revenue, topDrivers, recentOrders, distribution);

        assertThat(overview.kpi()).isEqualTo(kpi);
        assertThat(overview.revenueChart()).isEqualTo(revenue);
        assertThat(overview.topDrivers()).isEqualTo(topDrivers);
        assertThat(overview.recentOrders()).isEqualTo(recentOrders);
        assertThat(overview.statusDistribution()).isEqualTo(distribution);
        assertThat(overview).isEqualTo(overview);
        assertThat(overview.hashCode()).isEqualTo(overview.hashCode());
        assertThat(overview.toString()).contains("kpi");
    }

    @Test
    void dashboardOverviewResponseAllowsNullSubFieldsPerSpecFallback() {
        DashboardOverviewResponse overview = new DashboardOverviewResponse(null, null, null, null, null);

        assertThat(overview.kpi()).isNull();
        assertThat(overview.revenueChart()).isNull();
        assertThat(overview.topDrivers()).isNull();
        assertThat(overview.recentOrders()).isNull();
        assertThat(overview.statusDistribution()).isNull();
    }
}
