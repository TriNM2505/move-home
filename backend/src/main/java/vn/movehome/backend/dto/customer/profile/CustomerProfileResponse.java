package vn.movehome.backend.dto.customer.profile;

import java.time.Instant;
import java.util.UUID;

public record CustomerProfileResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        String status,
        Instant createdAt,
        long totalOrders
) {
}
