package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.UserAccountStatusResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.RefreshTokenRepository;
import vn.movehome.backend.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserAccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditService auditService;

    private AdminUserAccountService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserAccountService(userRepository, refreshTokenRepository, auditService);
    }

    @Test
    void lockThenUnlockChangesLoginStatusAndWritesAudit() {
        UUID userId = UUID.randomUUID();
        User target = user(userId, "customer@movehome.vn", UserRole.CUSTOMER, UserStatus.ACTIVE);
        User admin = user(UUID.randomUUID(), "admin@movehome.vn", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        UserAccountStatusResponse locked = service.updateStatus(userId, UserStatus.LOCKED, admin);

        assertThat(locked.status()).isEqualTo(UserStatus.LOCKED);
        assertThat(target.isAccountNonLocked()).isFalse();
        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(auditService).log(
                admin.getId(), admin.getEmail(), "USER_ACCOUNT_LOCKED", "USER",
                userId.toString(), "ACTIVE -> LOCKED");

        UserAccountStatusResponse active = service.updateStatus(userId, UserStatus.ACTIVE, admin);

        assertThat(active.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(target.isAccountNonLocked()).isTrue();
        verify(auditService).log(
                admin.getId(), admin.getEmail(), "USER_ACCOUNT_UNLOCKED", "USER",
                userId.toString(), "LOCKED -> ACTIVE");
    }

    @Test
    void pendingDriverCannotBeLockedBecauseApprovalStateMustNotBeOverwritten() {
        UUID userId = UUID.randomUUID();
        User target = user(userId, "driver@movehome.vn", UserRole.DRIVER, UserStatus.PENDING_APPROVAL);
        User admin = user(UUID.randomUUID(), "admin@movehome.vn", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.updateStatus(userId, UserStatus.LOCKED, admin))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).startsWith("INVALID_STATUS_TRANSITION|");
                });

        assertThat(target.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        verify(userRepository, never()).saveAndFlush(target);
        verify(auditService, never()).log(
                admin.getId(), admin.getEmail(), "USER_ACCOUNT_LOCKED", "USER",
                userId.toString(), "PENDING_APPROVAL -> LOCKED");
    }

    private User user(UUID id, String email, UserRole role, UserStatus status) {
        return User.builder()
                .id(id)
                .email(email)
                .role(role)
                .status(status)
                .emailVerified(true)
                .build();
    }
}
