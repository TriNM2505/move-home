package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.movehome.backend.entity.AuditLog;

import java.util.UUID;

/** Ghi audit theo nguyên tắc best-effort: lỗi audit không phá nghiệp vụ chính. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogWriter auditLogWriter;

    public void log(
            UUID actorId,
            String actorEmail,
            String action,
            String entityType,
            String entityId,
            String detail
    ) {
        try {
            auditLogWriter.persist(AuditLog.builder()
                    .actorId(actorId)
                    .actorEmail(actorEmail)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .detail(detail)
                    .build());
        } catch (Exception exception) {
            // Không log actor email/detail để tránh rò rỉ PII hoặc nội dung nhạy cảm.
            log.warn("Không thể ghi audit action={}, entityType={}, entityId={}: {}",
                    action, entityType, entityId, exception.getMessage());
        }
    }
}
