package vn.movehome.backend.dto.driver.onboarding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DriverProfileResponse(
        String licenseNumber,
        String licenseClass,
        LocalDate licenseExpiryDate,
        String vehiclePlate,
        String vehicleType,
        Integer vehicleCapacityKg,
        OffsetDateTime approvedAt,
        Integer totalOrdersCompleted,
        BigDecimal averageRating,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String onboardingStatus
) {
}
