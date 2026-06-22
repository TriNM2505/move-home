package vn.movehome.backend.dto.admin;

import java.time.Instant;

/** Contract dữ liệu của audit-log.html. */
public record AuditLogResponse(
        String actorEmail,
        String action,
        String entityType,
        String entityId,
        String detail,
        Instant createdAt
) {
}
