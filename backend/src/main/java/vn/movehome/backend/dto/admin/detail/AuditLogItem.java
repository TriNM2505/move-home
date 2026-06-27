package vn.movehome.backend.dto.admin.detail;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogItem(
        @JsonProperty("id") UUID id,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("actor_id") UUID actorId,
        @JsonProperty("actor_email") String actorEmail,
        @JsonProperty("entity_type") String entityType,
        @JsonProperty("entity_id") String entityId,
        @JsonProperty("detail") String detail,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
}
