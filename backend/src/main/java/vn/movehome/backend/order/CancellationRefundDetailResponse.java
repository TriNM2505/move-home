package vn.movehome.backend.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Chi tiet 1 yeu cau hoan coc cho Manager xem truoc khi duyet. */
public record CancellationRefundDetailResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        String orderStatus,
        UUID customerId,
        String customerName,
        String customerPhone,
        String reason,
        String status,
        BigDecimal depositAmount,
        BigDecimal refundAmount,
        String rejectionReason,
        UUID processedBy,
        OffsetDateTime processedAt,
        OffsetDateTime createdAt,
        List<String> photoUrls,
        OrderSummary order
) {
    public record OrderSummary(
            String pickupAddress,
            String dropoffAddress,
            BigDecimal totalQuote,
            OffsetDateTime scheduledAt
    ) {
    }
}
