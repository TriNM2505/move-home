package vn.movehome.backend.dto.admin;

/**
 * Combined response cho GET /api/admin/dashboard/overview (Spec #028).
 * Frontend goi 1 request thay vi 5 request rieng le (Spec #028 FR-016: Promise.all).
 * Moi field la nullable — neu 1 sub-query loi, cac field khac van co the tra ve.
 */
public record DashboardOverviewResponse(
    KpiResponse kpi,
    RevenueByDayResponse revenueChart,
    DriverPerformanceResponse topDrivers,
    RecentOrderResponse recentOrders,
    OrderStatusDistribution statusDistribution
) {}
