package vn.movehome.backend.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverDetailResponse(
        @JsonProperty("driver_id") UUID driverId,
        @JsonProperty("full_name") String fullName,
        String email,
        String phone,
        String status,
        @JsonProperty("license_number") String licenseNumber,
        @JsonProperty("license_class") String licenseClass,
        @JsonProperty("license_expiry_date") LocalDate licenseExpiryDate,
        @JsonProperty("vehicle_plate") String vehiclePlate,
        @JsonProperty("vehicle_type") String vehicleType,
        @JsonProperty("vehicle_capacity_kg") Integer vehicleCapacityKg,
        @JsonProperty("deposit_amount") BigDecimal depositAmount,
        @JsonProperty("deposit_paid_at") OffsetDateTime depositPaidAt,
        @JsonProperty("onboarding_completed_at") OffsetDateTime onboardingCompletedAt,
        @JsonProperty("approved_at") OffsetDateTime approvedAt,
        @JsonProperty("approved_by_manager_id") UUID approvedByManagerId,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("document_count") long documentCount
) {}
