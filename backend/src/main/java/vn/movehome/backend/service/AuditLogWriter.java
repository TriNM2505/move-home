package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.repository.AuditLogRepository;

/**
 * Tách transaction ghi audit khỏi transaction nghiệp vụ chính.
 * saveAndFlush buộc lỗi DB phát sinh bên trong boundary có thể được AuditService xử lý.
 */
@Service
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AuditLog auditLog) {
        auditLogRepository.saveAndFlush(auditLog);
    }
}
