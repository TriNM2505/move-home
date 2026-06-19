package vn.movehome.backend.order;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CancelOrderResponse(
        UUID orderId,
        String status,
        OffsetDateTime cancelledAt,
        String message
) {
}
