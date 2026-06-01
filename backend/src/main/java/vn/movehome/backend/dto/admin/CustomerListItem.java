package vn.movehome.backend.dto.admin;

import vn.movehome.backend.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO cho 1 dong trong bang danh sach khach hang cua Admin (Phase 3C).
 * Endpoint: GET /api/admin/dashboard/customers
 * totalOrdersPlaced: dem tu service_order (ke ca don CANCELLED).
 * lastLoginAt: chua co tracking trong v1 — luon tra ve null.
 */
public record CustomerListItem(
        UUID id,
        String fullName,
        String email,
        String phone,                    // null neu chua cap nhat
        UserStatus status,               // ACTIVE / PENDING_VERIFY / SUSPENDED
        Boolean emailVerified,
        Long totalOrdersPlaced,          // so don da dat (ke ca cancel)
        Instant createdAt,
        Instant lastLoginAt             // null: chua implement tracking trong v1
) {}
