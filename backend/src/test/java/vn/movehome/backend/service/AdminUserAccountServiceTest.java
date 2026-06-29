package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.ActivateUserRequest;
import vn.movehome.backend.dto.admin.SuspendUserRequest;
import vn.movehome.backend.dto.admin.UserAccountStatusResponse;
import vn.movehome.backend.dto.admin.UserSuspensionActionResponse;
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

    @Test
    void suspendCustomerStoresSuspensionFieldsRevokesTokensAndWritesAudit() {
        UUID userId = UUID.randomUUID();
        User target = user(userId, "customer@movehome.vn", UserRole.CUSTOMER, UserStatus.ACTIVE);
        User admin = user(UUID.randomUUID(), "admin@movehome.vn", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));

        UserSuspensionActionResponse response = service.suspend(
                userId,
                new SuspendUserRequest("Suspicious payment activity requires manual account review.", 30),
                admin);

        assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(response.previousStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(target.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(target.getSuspensionPreviousStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(target.getSuspendedAt()).isNotNull();
        assertThat(target.getSuspendedBy()).isEqualTo(admin.getId());
        assertThat(target.getSuspensionReason()).isEqualTo(
                "Suspicious payment activity requires manual account review.");
        assertThat(target.getSuspensionUntil()).isNotNull();
        assertThat(target.isAccountNonLocked()).isFalse();
        verify(userRepository).saveAndFlush(target);
        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(auditService).log(
                admin.getId(), admin.getEmail(), "USER_SUSPENDED", "USER",
                userId.toString(),
                "previous_status=ACTIVE; new_status=SUSPENDED; duration_days=30");
    }

    @Test
    void activateSuspendedDriverRestoresPreviousStatusAndClearsSuspensionFields() {
        UUID userId = UUID.randomUUID();
        User target = user(userId, "driver@movehome.vn", UserRole.DRIVER, UserStatus.SUSPENDED);
        target.setSuspensionPreviousStatus(UserStatus.PENDING_APPROVAL);
        target.setSuspendedBy(UUID.randomUUID());
        target.setSuspensionReason("Suspicious payment activity requires manual account review.");
        target.setSuspendedAt(java.time.Instant.now());
        target.setSuspensionUntil(java.time.Instant.now().plusSeconds(86_400));
        User admin = user(UUID.randomUUID(), "admin@movehome.vn", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(target));

        UserSuspensionActionResponse response = service.activate(
                userId,
                new ActivateUserRequest("Manual review completed."),
                admin);

        assertThat(response.status()).isEqualTo(UserStatus.PENDING_APPROVAL);
        assertThat(target.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        assertThat(target.getSuspensionPreviousStatus()).isNull();
        assertThat(target.getSuspendedAt()).isNull();
        assertThat(target.getSuspendedBy()).isNull();
        assertThat(target.getSuspensionReason()).isNull();
        assertThat(target.getSuspensionUntil()).isNull();
        verify(userRepository).saveAndFlush(target);
        verify(refreshTokenRepository, never()).revokeAllByUserId(userId);
        verify(auditService).log(
                admin.getId(), admin.getEmail(), "USER_REACTIVATED", "USER",
                userId.toString(),
                "previous_status=SUSPENDED; restored_status=PENDING_APPROVAL");
    }

    @Test
    void suspendRejectsSelfAndStaffTargets() {
        UUID adminId = UUID.randomUUID();
        User admin = user(adminId, "admin@movehome.vn", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(adminId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.suspend(
                adminId,
                new SuspendUserRequest("Suspicious payment activity requires manual account review.", null),
                admin))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).startsWith("CANNOT_SUSPEND_SELF|");
                });

        UUID managerId = UUID.randomUUID();
        User manager = user(managerId, "manager@movehome.vn", UserRole.MANAGER, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(managerId)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> service.suspend(
                managerId,
                new SuspendUserRequest("Suspicious payment activity requires manual account review.", null),
                admin))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).startsWith("STAFF_SUSPENSION_OUT_OF_SCOPE|");
                });

        verify(userRepository, never()).saveAndFlush(manager);
    }

    @Test
    void suspendRejectsInvalidReasonAndDurationBeforeLockingTarget() {
        User admin = user(UUID.randomUUID(), "admin@movehome.vn", UserRole.ADMIN, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.suspend(
                UUID.randomUUID(),
                new SuspendUserRequest("too short", null),
                admin))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).startsWith("INVALID_SUSPENSION_REASON|");
                });

        assertThatThrownBy(() -> service.suspend(
                UUID.randomUUID(),
                new SuspendUserRequest("Suspicious payment activity requires manual account review.", 366),
                admin))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).startsWith("INVALID_SUSPENSION_DURATION|");
                });
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
