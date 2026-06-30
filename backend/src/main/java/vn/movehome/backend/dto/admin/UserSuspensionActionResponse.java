package vn.movehome.backend.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import vn.movehome.backend.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserSuspensionActionResponse(
        String message,
        @JsonProperty("user_id") UUID userId,
        UserStatus status,
        @JsonProperty("previous_status") UserStatus previousStatus,
        @JsonProperty("suspended_at") Instant suspendedAt,
        @JsonProperty("suspension_until") Instant suspensionUntil
) {
}

