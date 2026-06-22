package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.AuditLogResponse;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.repository.AuditLogRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    public Page<AuditLogResponse> findAuditLogs(
            String action,
            String entityType,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_TIME_RANGE|Thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.");
        }

        Specification<AuditLog> filters = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (hasText(action)) {
            filters = filters.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("action"), action.trim()));
        }
        if (hasText(entityType)) {
            filters = filters.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("entityType"), entityType.trim()));
        }
        if (from != null) {
            filters = filters.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            filters = filters.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        return auditLogRepository.findAll(filters, pageable).map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getActorEmail(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getDetail(),
                auditLog.getCreatedAt()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
