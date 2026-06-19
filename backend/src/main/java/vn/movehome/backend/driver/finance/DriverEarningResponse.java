package vn.movehome.backend.driver.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverEarningResponse(
        UUID id,
        BigDecimal amount,
        UUID relatedOrderId,
        String orderCode,
        String description,
        OffsetDateTime createdAt
) {
}
