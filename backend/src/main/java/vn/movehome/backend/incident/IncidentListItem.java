package vn.movehome.backend.incident;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Mot dong trong hang doi "Su co van chuyen" cua Manager. */
public record IncidentListItem(
        UUID id,
        UUID orderId,
        String orderCode,
        UUID driverId,
        String driverName,
        String reason,
        String status,
        // Trang thai don luc bao su co (ACCEPTED | IN_PROGRESS).
        String orderStatusSnapshot,
        // Han 15 phut de tai xe khac nhan lai (null khi con PENDING).
        OffsetDateTime reassignDeadline,
        // true khi da CONFIRMED + qua han 15 phut ma chua ai nhan -> Manager duoc hoan coc + boi thuong.
        boolean overdue,
        OffsetDateTime createdAt,
        OffsetDateTime confirmedAt,
        BigDecimal refundAmount,
        BigDecimal penaltyAmount
) {
}
