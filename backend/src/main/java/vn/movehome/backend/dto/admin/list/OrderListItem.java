package vn.movehome.backend.dto.admin.list;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderListItem(
        @JsonProperty("id") UUID id,
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("driver_name") String driverName,
        @JsonProperty("vehicle_type") String vehicleType,
        @JsonProperty("pickup_district") String pickupDistrict,
        @JsonProperty("dropoff_district") String dropoffDistrict,
        @JsonProperty("total_quote") BigDecimal totalQuote,
        @JsonProperty("status") String status,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("scheduled_at") OffsetDateTime scheduledAt
) {
}
