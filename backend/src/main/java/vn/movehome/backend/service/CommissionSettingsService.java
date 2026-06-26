package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.finance.CommissionSettingsResponse;
import vn.movehome.backend.dto.admin.finance.UpdateCommissionSettingsRequest;
import vn.movehome.backend.dto.admin.finance.UpdateCommissionSettingsResponse;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.entity.CommissionSettings;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.repository.AuditLogRepository;
import vn.movehome.backend.repository.CommissionSettingsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class CommissionSettingsService {

    private static final BigDecimal MIN_COMMISSION_RATE = new BigDecimal("0.0500");
    private static final BigDecimal MAX_COMMISSION_RATE = new BigDecimal("0.5000");

    private final CommissionSettingsRepository commissionSettingsRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public CommissionSettingsResponse getCurrent() {
        return toResponse(loadActive());
    }

    @Transactional(readOnly = true)
    public BigDecimal currentCommissionRate() {
        return normalizeRate(loadActive().getCommissionRate());
    }

    @Transactional
    public UpdateCommissionSettingsResponse update(User admin, UpdateCommissionSettingsRequest request) {
        if (request == null || request.commissionRate() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Ty le hoa hong bat buoc.");
        }

        BigDecimal newRate = normalizeRate(request.commissionRate());
        CommissionSettings settings = commissionSettingsRepository.findActiveForUpdate()
                .orElseThrow(this::settingsUnavailable);

        if (request.version() != null && !request.version().equals(settings.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SETTINGS_VERSION_CONFLICT|Cau hinh da thay doi, vui long tai lai.");
        }
        BigDecimal oldRate = normalizeRate(settings.getCommissionRate());
        if (oldRate.compareTo(newRate) == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NO_SETTINGS_CHANGED|Khong co thay doi cau hinh.");
        }

        Long oldVersion = settings.getVersion();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        settings.setCommissionRate(newRate);
        settings.setVersion(oldVersion + 1);
        settings.setLastUpdatedAt(now);
        settings.setLastUpdatedBy(admin.getId());

        commissionSettingsRepository.saveAndFlush(settings);
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorId(admin.getId())
                .actorEmail(admin.getEmail())
                .action("SETTINGS_UPDATED")
                .entityType("COMMISSION_SETTINGS")
                .entityId("1")
                .detail("commission_rate: " + oldRate + " -> " + newRate
                        + ", version: " + oldVersion + " -> " + settings.getVersion())
                .build());

        CommissionSettingsResponse response = toResponse(settings);
        return new UpdateCommissionSettingsResponse(
                "Da cap nhat cau hinh",
                response,
                now,
                settings.getVersion()
        );
    }

    private CommissionSettings loadActive() {
        return commissionSettingsRepository.findActive()
                .orElseThrow(this::settingsUnavailable);
    }

    private ResponseStatusException settingsUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "SETTINGS_UNAVAILABLE|Khong the tai cau hinh hien hanh.");
    }

    private BigDecimal normalizeRate(BigDecimal value) {
        BigDecimal normalized;
        try {
            normalized = value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Ty le hoa hong toi da 4 chu so thap phan.");
        }
        if (normalized.compareTo(MIN_COMMISSION_RATE) < 0 || normalized.compareTo(MAX_COMMISSION_RATE) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Ty le hoa hong phai tu 0.0500 den 0.5000.");
        }
        return normalized;
    }

    private CommissionSettingsResponse toResponse(CommissionSettings settings) {
        return new CommissionSettingsResponse(
                settings.getVersion(),
                normalizeRate(settings.getCommissionRate()),
                settings.getLastUpdatedAt(),
                settings.getLastUpdatedBy()
        );
    }
}
