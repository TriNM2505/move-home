package vn.movehome.backend.dto.admin;

import java.math.BigDecimal;
import java.util.List;

/**
 * Du lieu bieu do doanh thu 30 ngay (Spec #028 FR-010, FR-012).
 * Luon co du 30 phan tu — ngay khong co don tra ve 0 (FR-012: khong bo trong).
 */
public record RevenueByDayResponse(List<RevenuePoint> points) {

    /**
     * Mot ngay trong bieu do.
     * date: "yyyy-MM-dd" theo Asia/Ho_Chi_Minh (AC-07).
     * revenue: tong total_quote (VND nguyen dong — AC-08).
     * commission: tong total_quote * commission_rate (30%).
     * orderCount: so don tao trong ngay (bat ky status).
     */
    public record RevenuePoint(
        String date,
        BigDecimal revenue,
        BigDecimal commission,
        long orderCount
    ) {}
}
