package vn.movehome.backend.dispute;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DisputeDetailResponse(
        UUID id,
        String status,
        String claimType,
        BigDecimal claimAmount,
        String customerStatement,
        String driverResponse,
        OffsetDateTime driverResponseAt,
        BigDecimal resolutionAmount,
        String resolutionNote,
        UUID resolvedBy,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime deadline,
        BigDecimal pendingDeductShortfall,
        OffsetDateTime deductDeadline,
        List<String> photoUrls,
        OrderSummary order,
        PartySummary customer,
        PartySummary driver
) {

    public record OrderSummary(
            UUID id,
            String orderCode,
            String status,
            BigDecimal totalQuote,
            OffsetDateTime scheduledAt,
            OffsetDateTime completedAt,
            String pickupAddress,
            String dropoffAddress,
            String vehicleType
    ) {
    }

    public record PartySummary(
            UUID id,
            String fullName,
            String phone
    ) {
    }
}
