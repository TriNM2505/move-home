package vn.movehome.backend.dto.admin.list;

import com.fasterxml.jackson.annotation.JsonProperty;

import vn.movehome.backend.entity.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record CustomerListItem(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("email") String email,
        @JsonProperty("phone") String phone,
        @JsonProperty("status") String status,
        @JsonProperty("total_orders") Long totalOrders,
        @JsonProperty("total_spent") BigDecimal totalSpent,
        @JsonProperty("wallet_balance") BigDecimal walletBalance,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("last_active_at") OffsetDateTime lastActiveAt,
        @JsonProperty("email_verified") boolean emailVerified
) {
    public CustomerListItem(
            UUID userId,
            String fullName,
            String email,
            String phone,
            UserStatus status,
            Long totalOrders,
            BigDecimal totalSpent,
            BigDecimal walletBalance,
            Instant createdAt,
            Instant lastActiveAt,
            boolean emailVerified
    ) {
        this(
                userId,
                fullName,
                email,
                phone,
                status != null ? status.name() : null,
                totalOrders,
                totalSpent,
                walletBalance,
                toOffsetDateTime(createdAt),
                toOffsetDateTime(lastActiveAt),
                emailVerified
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }
}
