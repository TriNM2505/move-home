package vn.movehome.backend.dispute;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DisputeListItemResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        String orderStatus,
        UUID customerId,
        String customerName,
        UUID driverId,
        String driverName,
        String claimType,
        BigDecimal claimAmount,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime deadline
) {
}
