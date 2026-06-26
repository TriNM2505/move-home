package vn.movehome.backend.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PendingDriverItem(
        @JsonProperty("driver_id") UUID driverId,
        @JsonProperty("full_name") String fullName,
        String email,
        String phone,
        @JsonProperty("vehicle_plate") String vehiclePlate,
        @JsonProperty("vehicle_type") String vehicleType,
        @JsonProperty("deposit_amount") BigDecimal depositAmount,
        @JsonProperty("deposit_paid_at") OffsetDateTime depositPaidAt,
        @JsonProperty("onboarding_completed_at") OffsetDateTime onboardingCompletedAt,
        @JsonProperty("document_count") long documentCount
) {}
