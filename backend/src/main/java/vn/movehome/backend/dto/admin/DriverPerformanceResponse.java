package vn.movehome.backend.dto.admin;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Top 5 Driver co tong doanh thu cao nhat (Admin Dashboard leaderboard).
 * averageRating: BigDecimal (0.00-5.00) de khop voi kieu DB NUMERIC(3,2).
 */
public record DriverPerformanceResponse(List<DriverStat> topDrivers) {

    public record DriverStat(
        UUID driverId,
        String fullName,
        long totalOrders,
        BigDecimal totalRevenue,    // VND, scale=0 (AC-08)
        BigDecimal averageRating    // 0.00–5.00, NUMERIC(3,2)
    ) {}
}
