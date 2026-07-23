package vn.movehome.backend.repository;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.entity.CommissionSettings;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiem tra default method CommissionSettingsRepository#findActive():
 * phai uy quyen cho findById(1) — commission_settings luon la 1 hang duy nhat (id=1).
 * Mock interface voi CALLS_REAL_METHODS de goi that default method (khong the test
 * qua real JPA proxy trong unit test thuan).
 */
class CommissionSettingsRepositoryTest {

    @Test
    void findActiveDelegatesToFindByIdWithFixedId1() {
        CommissionSettingsRepository repository = mock(CommissionSettingsRepository.class, CALLS_REAL_METHODS);
        CommissionSettings settings = new CommissionSettings();
        settings.setId(1);
        when(repository.findById(1)).thenReturn(Optional.of(settings));

        Optional<CommissionSettings> result = repository.findActive();

        assertThat(result).contains(settings);
        verify(repository).findById(1);
    }

    @Test
    void findActiveReturnsEmptyWhenNoRowWithId1Exists() {
        CommissionSettingsRepository repository = mock(CommissionSettingsRepository.class, CALLS_REAL_METHODS);
        when(repository.findById(1)).thenReturn(Optional.empty());

        Optional<CommissionSettings> result = repository.findActive();

        assertThat(result).isEmpty();
        verify(repository).findById(1);
    }
}
