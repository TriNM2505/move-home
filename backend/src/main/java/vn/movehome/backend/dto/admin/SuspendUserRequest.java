package vn.movehome.backend.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SuspendUserRequest(
        String reason,
        @JsonProperty("duration_days") Integer durationDays
) {
}

