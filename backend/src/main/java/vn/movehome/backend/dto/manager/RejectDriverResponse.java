package vn.movehome.backend.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record RejectDriverResponse(
        @JsonProperty("driver_id") UUID driverId,
        String status,
        String message,
        @JsonProperty("can_resubmit") boolean canResubmit
) {}
