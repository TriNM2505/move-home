package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.finance.UpdateCommissionSettingsRequest;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.entity.CommissionSettings;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.repository.AuditLogRepository;
import vn.movehome.backend.repository.CommissionSettingsRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionSettingsServiceTest {

    @Mock
    private CommissionSettingsRepository commissionSettingsRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private CommissionSettingsService service;

    @BeforeEach
    void setUp() {
        service = new CommissionSettingsService(commissionSettingsRepository, auditLogRepository);
    }

    @Test
    void updateCommissionRateIncrementsVersionAndWritesAudit() {
        CommissionSettings settings = settings("0.3000", 7L);
        User admin = admin();
        when(commissionSettingsRepository.findActiveForUpdate()).thenReturn(Optional.of(settings));

        service.update(admin, new UpdateCommissionSettingsRequest(7L, new BigDecimal("0.2500")));

        assertThat(settings.getCommissionRate()).isEqualByComparingTo("0.2500");
        assertThat(settings.getVersion()).isEqualTo(8L);
        assertThat(settings.getLastUpdatedBy()).isEqualTo(admin.getId());
        verify(commissionSettingsRepository).saveAndFlush(settings);
        verify(auditLogRepository).saveAndFlush(any(AuditLog.class));
    }

    @Test
    void updateRejectsStaleVersion() {
        CommissionSettings settings = settings("0.3000", 8L);
        when(commissionSettingsRepository.findActiveForUpdate()).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> service.update(admin(),
                new UpdateCommissionSettingsRequest(7L, new BigDecimal("0.2500"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(commissionSettingsRepository, never()).saveAndFlush(any());
        verify(auditLogRepository, never()).saveAndFlush(any());
    }

    private CommissionSettings settings(String rate, Long version) {
        CommissionSettings settings = new CommissionSettings();
        settings.setId(1);
        settings.setCommissionRate(new BigDecimal(rate));
        settings.setVersion(version);
        settings.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return settings;
    }

    private User admin() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("admin@movehome.vn")
                .role(UserRole.ADMIN)
                .build();
    }
}
