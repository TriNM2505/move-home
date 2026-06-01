package vn.movehome.backend.dto.admin;

import vn.movehome.backend.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 10 don hang gan nhat cho bang Admin Dashboard (Spec #028 FR-014, FR-015).
 * driverName = null khi chua co Driver phan cong (FR-015).
 */
public record RecentOrderResponse(List<RecentOrderItem> orders) {

    public record RecentOrderItem(
        UUID orderId,
        String orderCode,
        String customerName,
        String driverName,       // null khi status=PENDING (FR-015)
        OrderStatus status,
        BigDecimal totalQuote,   // VND, scale=0 (AC-08)
        Instant createdAt        // UTC Instant, FE convert sang Asia/Ho_Chi_Minh (AC-07)
    ) {}
}
