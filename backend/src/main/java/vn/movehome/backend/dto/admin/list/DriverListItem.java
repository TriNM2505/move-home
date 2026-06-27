package vn.movehome.backend.dto.admin.list;

import com.fasterxml.jackson.annotation.JsonProperty;

import vn.movehome.backend.entity.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record DriverListItem(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("email") String email,
        @JsonProperty("phone") String phone,
        @JsonProperty("vehicle_type") String vehicleType,
        @JsonProperty("license_plate") String licensePlate,
        @JsonProperty("status") String status,
        @JsonProperty("average_rating") BigDecimal averageRating,
        @JsonProperty("total_completed_orders") Long totalCompletedOrders,
        @JsonProperty("total_earnings") BigDecimal totalEarnings,
        @JsonProperty("current_balance") BigDecimal currentBalance,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("last_active_at") OffsetDateTime lastActiveAt
) {
    public DriverListItem(
            UUID userId,
            String fullName,
            String email,
            String phone,
            String vehicleType,
            String licensePlate,
            UserStatus status,
            BigDecimal averageRating,
            Integer totalCompletedOrders,
            BigDecimal totalEarnings,
            BigDecimal currentBalance,
            Instant createdAt,
            Instant lastActiveAt
    ) {
        this(
                userId,
                fullName,
                email,
                phone,
                vehicleType,
                licensePlate,
                status != null ? status.name() : null,
                averageRating,
                totalCompletedOrders != null ? totalCompletedOrders.longValue() : null,
                totalEarnings,
                currentBalance,
                toOffsetDateTime(createdAt),
                toOffsetDateTime(lastActiveAt)
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }
}
