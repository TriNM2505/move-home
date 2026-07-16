package vn.movehome.backend.incident;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Chi tiet 1 su co van chuyen cho Manager xu ly. */
public record IncidentDetailResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        // Trang thai don HIEN TAI (de biet da co tai xe nhan lai chua).
        String orderStatusCurrent,
        String orderStatusSnapshot,
        UUID driverId,
        String driverName,
        String driverPhone,
        UUID customerId,
        String customerName,
        String customerPhone,
        String reason,
        String status,
        OffsetDateTime reassignDeadline,
        boolean overdue,
        OffsetDateTime confirmedAt,
        // Uoc tinh coc 30% = total_quote * commission_rate_snapshot (floor).
        BigDecimal depositEstimate,
        // Muc boi thuong co dinh cho khach (200.000).
        BigDecimal compensationAmount,
        // So tien da hoan khach + so tien da tru tai xe khi COMPENSATED (null truoc do).
        BigDecimal refundAmount,
        BigDecimal penaltyAmount,
        OffsetDateTime createdAt,
        List<String> photoUrls,
        OrderSummary order
) {
    public record OrderSummary(
            String pickupAddress,
            String dropoffAddress,
            BigDecimal totalQuote,
            OffsetDateTime scheduledAt
    ) {
    }
}
