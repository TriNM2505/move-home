package vn.movehome.backend.order;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderResponse(
        UUID id,
        String orderCode,
        String status,
        BigDecimal totalQuote
) {
}
