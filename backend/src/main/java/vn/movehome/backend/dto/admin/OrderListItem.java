package vn.movehome.backend.dto.admin;

import vn.movehome.backend.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO cho 1 dong trong bang danh sach don hang cua Admin (Phase 3C).
 * Endpoint: GET /api/admin/dashboard/orders
 * Spec #028, AC-08: totalQuote va commissionRateSnapshot la BigDecimal, khong float/double.
 */
public record OrderListItem(
        UUID id,
        String orderCode,
        String customerName,
        String driverName,               // null neu chua co driver duoc phan cong
        OrderStatus status,
        String pickupDistrict,           // null neu chua dien
        String dropoffDistrict,          // null neu chua dien
        BigDecimal totalQuote,           // VND nguyen dong, scale=0 (AC-08)
        BigDecimal commissionRateSnapshot,
        Instant scheduledAt,
        Instant createdAt,
        Instant completedAt              // null neu don chua COMPLETED
) {}
