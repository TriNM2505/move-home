package vn.movehome.backend.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chi tiet 1 don cho Customer, bao gom thong tin cọc da tra (30%) va phan con lai (70%).
 */
public record CustomerOrderDetailResponse(
        UUID id,
        String orderCode,
        String status,
        String vehicleType,
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
        BigDecimal baseFare,
        BigDecimal peakSurcharge,
        BigDecimal alleySurcharge,
        BigDecimal floorSurcharge,
        BigDecimal porterFee,
        Integer porterCount,
        BigDecimal totalQuote,
        // Thanh toan 2 giai doan
        BigDecimal depositAmount,     // coc 30%
        boolean depositPaid,          // da tra coc chua (status vuot PENDING_PAYMENT)
        BigDecimal remainingAmount,   // con lai 70%
        boolean finalPaid,            // da tra not 70% chua
        String driverName,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        OffsetDateTime cancelledAt
) {
}
