package vn.movehome.backend.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Kiem tra default method RefreshTokenRepository#revokeAllByUserId(UUID):
 * phai uy quyen cho revokeAllByUserIdAt(userId, Instant.now()) (PANIC MODE — FR-029).
 * Mock interface voi CALLS_REAL_METHODS de goi that default method.
 */
class RefreshTokenRepositoryTest {

    @Test
    void revokeAllByUserIdDelegatesToRevokeAllByUserIdAtWithCurrentInstant() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class, CALLS_REAL_METHODS);
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        repository.revokeAllByUserId(userId);

        Instant after = Instant.now();
        ArgumentCaptor<Instant> revokedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).revokeAllByUserIdAt(eq(userId), revokedAtCaptor.capture());
        assertThat(revokedAtCaptor.getValue()).isBetween(before, after);
    }
}
