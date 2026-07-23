package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.finance.CommissionSettingsResponse;
import vn.movehome.backend.dto.admin.finance.UpdateCommissionSettingsRequest;
import vn.movehome.backend.dto.admin.finance.UpdateCommissionSettingsResponse;
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

    @Test
    void getCurrentReturnsMappedResponse() {
        CommissionSettings settings = settings("0.3000", 5L);
        UUID updatedBy = UUID.randomUUID();
        settings.setLastUpdatedBy(updatedBy);
        when(commissionSettingsRepository.findActive()).thenReturn(Optional.of(settings));

        CommissionSettingsResponse response = service.getCurrent();

        assertThat(response.version()).isEqualTo(5L);
        assertThat(response.commissionRate()).isEqualByComparingTo("0.3000");
        assertThat(response.lastUpdatedBy()).isEqualTo(updatedBy);
    }

    @Test
    void getCurrentThrowsServiceUnavailableWhenSettingsMissing() {
        when(commissionSettingsRepository.findActive()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent())
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void currentCommissionRateReturnsNormalizedRate() {
        CommissionSettings settings = settings("0.3000", 5L);
        when(commissionSettingsRepository.findActive()).thenReturn(Optional.of(settings));

        assertThat(service.currentCommissionRate()).isEqualByComparingTo("0.3000");
    }

    @Test
    void currentCommissionRateThrowsServiceUnavailableWhenSettingsMissing() {
        when(commissionSettingsRepository.findActive()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentCommissionRate())
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void updateThrowsWhenRequestIsNull() {
        assertThatThrownBy(() -> service.update(admin(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void updateThrowsWhenCommissionRateIsNull() {
        assertThatThrownBy(() -> service.update(admin(), new UpdateCommissionSettingsRequest(1L, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void updateThrowsServiceUnavailableWhenSettingsMissing() {
        when(commissionSettingsRepository.findActiveForUpdate()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(admin(),
                new UpdateCommissionSettingsRequest(1L, new BigDecimal("0.2500"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void updateSkipsVersionCheckWhenVersionIsNull() {
        CommissionSettings settings = settings("0.3000", 7L);
        when(commissionSettingsRepository.findActiveForUpdate()).thenReturn(Optional.of(settings));

        UpdateCommissionSettingsResponse response = service.update(admin(),
                new UpdateCommissionSettingsRequest(null, new BigDecimal("0.2500")));

        assertThat(settings.getVersion()).isEqualTo(8L);
        assertThat(response.version()).isEqualTo(8L);
        assertThat(response.message()).isEqualTo("Da cap nhat cau hinh");
    }

    @Test
    void updateThrowsWhenNoRateChange() {
        CommissionSettings settings = settings("0.3000", 7L);
        when(commissionSettingsRepository.findActiveForUpdate()).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> service.update(admin(),
                new UpdateCommissionSettingsRequest(7L, new BigDecimal("0.3000"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(commissionSettingsRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateThrowsWhenRateHasMoreThanFourDecimalPlaces() {
        assertThatThrownBy(() -> service.update(admin(),
                new UpdateCommissionSettingsRequest(7L, new BigDecimal("0.12345"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void updateThrowsWhenRateBelowMinimum() {
        assertThatThrownBy(() -> service.update(admin(),
                new UpdateCommissionSettingsRequest(7L, new BigDecimal("0.0100"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void updateThrowsWhenRateAboveMaximum() {
        assertThatThrownBy(() -> service.update(admin(),
                new UpdateCommissionSettingsRequest(7L, new BigDecimal("0.6000"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
