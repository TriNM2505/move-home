package vn.movehome.backend.dto.admin;

import vn.movehome.backend.entity.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO cho 1 dong trong bang danh sach tai xe cua Admin (Phase 3C).
 * Endpoint: GET /api/admin/dashboard/drivers
 * LEFT JOIN voi driver_profile — cac field profile co the null neu Driver chua submit ho so.
 * AC-08: depositAmount va totalRevenue la BigDecimal, khong float/double.
 */
public record DriverListItem(
        UUID userId,
        String fullName,
        String email,
        String phone,                    // null neu chua cap nhat
        UserStatus status,               // ACTIVE / PENDING_APPROVAL / PENDING_DOCUMENTS / etc.
        String licenseNumber,            // null neu chua nop GPLX
        String vehiclePlate,             // null neu chua nop dang ky xe
        String vehicleType,              // null neu chua nop ho so
        BigDecimal depositAmount,        // 0 neu chua dong coc; >= 3_000_000 sau khi dong
        Integer totalOrdersCompleted,    // 0 neu chua co don nao hoan thanh
        BigDecimal totalRevenue,         // VND, 0 neu chua co thu nhap
        BigDecimal averageRating,        // 0.00 neu chua co rating tu Customer
        Instant createdAt,
        Instant approvedAt              // null neu chua duoc Manager duyet
) {}
