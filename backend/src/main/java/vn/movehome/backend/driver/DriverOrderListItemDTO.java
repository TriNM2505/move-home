package vn.movehome.backend.driver;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverOrderListItemDTO(
        UUID id,
        String orderCode,
        String status,
        String vehicleType,
        String pickupDistrict,
        String dropoffDistrict,
        OffsetDateTime scheduledAt,
        BigDecimal distanceKm,
        BigDecimal totalQuote,
        OffsetDateTime createdAt
) {
}
