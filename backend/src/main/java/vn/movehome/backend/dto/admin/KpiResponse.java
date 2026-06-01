package vn.movehome.backend.dto.admin;

import java.math.BigDecimal;

/**
 * KPI tong quan cho Admin Dashboard (Spec #028 FR-004..FR-009).
 * money fields (AC-08): BigDecimal scale=0, serialize la integer VND.
 * calculated_at duoc them o tang Controller neu can.
 */
public record KpiResponse(
    long totalCustomers,
    long activeDrivers,
    long pendingDriverApprovals,
    long totalOrdersToday,           // Dem theo Asia/Ho_Chi_Minh (AC-07)
    long totalOrdersThisMonth,
    BigDecimal totalRevenueThisMonth,     // Tong total_quote cua COMPLETED orders thang nay
    BigDecimal totalCommissionThisMonth,  // Tong total_quote * rate cua COMPLETED orders thang nay
    long pendingOrders,
    long completedOrders,
    long inDisputeOrders
) {}
