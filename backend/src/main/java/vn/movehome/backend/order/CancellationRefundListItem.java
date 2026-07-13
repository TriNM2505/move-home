package vn.movehome.backend.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Mot dong trong hang doi "Yeu cau hoan coc" cua Manager. */
public record CancellationRefundListItem(
        UUID id,
        UUID orderId,
        String orderCode,
        UUID customerId,
        String customerName,
        String reason,
        String status,
        // Coc 30% du kien hoan (uoc tinh cho PENDING) = total_quote * commission_rate_snapshot.
        BigDecimal depositAmount,
        // So tien da hoan thuc te (null khi chua REFUNDED).
        BigDecimal refundAmount,
        OffsetDateTime createdAt,
        OffsetDateTime processedAt
) {
}
