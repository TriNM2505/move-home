package vn.movehome.backend.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApproveDriverResponse(
        @JsonProperty("driver_id") UUID driverId,
        String status,
        String message,
        @JsonProperty("approved_at") OffsetDateTime approvedAt
) {}
