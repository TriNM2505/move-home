package vn.movehome.backend.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RejectedDriverItem(
        @JsonProperty("driver_id") UUID driverId,
        @JsonProperty("full_name") String fullName,
        String email,
        String phone,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {}
