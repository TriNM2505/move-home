package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.movehome.backend.dto.admin.*;
import vn.movehome.backend.entity.OrderStatus;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.service.AdminDashboardService;

import java.util.List;

/**
 * Admin Dashboard endpoints (Spec #028 §5 API Summary).
 * Tat ca endpoint chi cho ADMIN — @PreAuthorize class-level (Constitution HR-10).
 * Khong co request body, khong co validation (cac param mac dinh dung trong service).
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    /** 10 KPI cards: so don, doanh thu, driver, tranh chap (FR-004..FR-009). */
    @GetMapping("/kpi")
    public KpiResponse getKpi() {
        return dashboardService.getKpi();
    }

    /** Bieu do doanh thu 30 ngay (FR-010, FR-012). */
    @GetMapping("/revenue-chart")
    public RevenueByDayResponse getRevenueChart() {
        return dashboardService.getRevenueByDay();
    }

    /** Top 5 Driver co doanh thu cao nhat (Spec #028 §1). */
    @GetMapping("/top-drivers")
    public DriverPerformanceResponse getTopDrivers() {
        return dashboardService.getTopDrivers();
    }

    /** 10 don hang gan nhat kem ten Customer va Driver (FR-014, FR-015). */
    @GetMapping("/recent-orders")
    public RecentOrderResponse getRecentOrders() {
        return dashboardService.getRecentOrders();
    }

    /** Phan bo so don theo tung trang thai (PENDING, COMPLETED...). */
    @GetMapping("/status-distribution")
    public OrderStatusDistribution getStatusDistribution() {
        return dashboardService.getStatusDistribution();
    }

    /**
     * Combined response — tat ca du lieu trong 1 call (Spec #028 FR-016).
     * Khuyen dung cho frontend de giam so round-trip.
     */
    @GetMapping("/overview")
    public DashboardOverviewResponse getOverview() {
        return dashboardService.getOverview();
    }

    // ===== Phase 3C: Admin List Endpoints =====

    /**
     * Danh sach don hang cho trang orders.html (Phase 3C).
     * Params: status (optional), page (default 0), size (default 50).
     * @PreAuthorize da duoc khai bao class-level — ADMIN only (Constitution HR-10, AC-04).
     */
    @GetMapping("/orders")
    public Page<OrderListItem> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return dashboardService.getAllOrders(status, PageRequest.of(page, size));
    }

    /**
     * Danh sach tai xe cho trang drivers.html (Phase 3C).
     * Param: status (optional) — ACTIVE / PENDING_APPROVAL / SUSPENDED / etc.
     */
    @GetMapping("/drivers")
    public List<DriverListItem> getAllDrivers(
            @RequestParam(required = false) UserStatus status) {
        return dashboardService.getAllDrivers(status);
    }

    /**
     * Danh sach khach hang cho trang customers.html (Phase 3C).
     * Param: status (optional) — ACTIVE / PENDING_VERIFY / SUSPENDED.
     */
    @GetMapping("/customers")
    public List<CustomerListItem> getAllCustomers(
            @RequestParam(required = false) UserStatus status) {
        return dashboardService.getAllCustomers(status);
    }
}
