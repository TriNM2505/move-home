package vn.movehome.backend.driver;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverOrderDetailDTO(
        UUID id,
        String orderCode,
        String status,
        boolean available,
        boolean assignedToCurrentDriver,
        boolean finalPaid,
        String customerName,
        String customerPhone,
        String vehicleType,
        Integer porterCount,
        String pickupAddress,
        String pickupDistrict,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        String dropoffAddress,
        String dropoffDistrict,
        BigDecimal dropoffLat,
        BigDecimal dropoffLng,
        OffsetDateTime scheduledAt,
        BigDecimal distanceKm,
        Integer estimatedDurationMinutes,
        BigDecimal baseFare,
        BigDecimal peakSurcharge,
        BigDecimal alleySurcharge,
        BigDecimal floorSurcharge,
        BigDecimal porterFee,
        BigDecimal totalQuote,
        String notes,
        UUID driverId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        OffsetDateTime cancelledAt,
        String cancellationReason,
        BigDecimal commissionRateSnapshot
) {
}
