package vn.movehome.backend.dto.admin.detail;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerOrderItem(
        @JsonProperty("id") UUID id,
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("status") String status,
        @JsonProperty("driver_name") String driverName,
        @JsonProperty("pickup_district") String pickupDistrict,
        @JsonProperty("dropoff_district") String dropoffDistrict,
        @JsonProperty("vehicle_type") String vehicleType,
        @JsonProperty("total_quote") BigDecimal totalQuote,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("scheduled_at") OffsetDateTime scheduledAt
) {
}
