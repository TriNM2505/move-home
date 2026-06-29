package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserAccountService {

    private static final int SUSPENSION_REASON_MIN_LENGTH = 30;
    private static final int SUSPENSION_REASON_MAX_LENGTH = 1000;
    private static final int SUSPENSION_DURATION_MIN_DAYS = 1;
    private static final int SUSPENSION_DURATION_MAX_DAYS = 365;
    private static final int ACTIVATION_NOTE_MAX_LENGTH = 1000;
    private static final Set<UserStatus> CUSTOMER_SUSPENDABLE_STATUSES =
            EnumSet.of(UserStatus.ACTIVE, UserStatus.PENDING_VERIFY);
    private static final Set<UserStatus> DRIVER_SUSPENDABLE_STATUSES =
            EnumSet.of(
                    UserStatus.ACTIVE,
                    UserStatus.PENDING_VERIFY,
                    UserStatus.PENDING_DOCUMENTS,
                    UserStatus.PENDING_DEPOSIT,
                    UserStatus.PENDING_APPROVAL,
                    UserStatus.REJECTED);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserAccountStatusResponse getStatus(UUID userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public UserAccountStatusResponse updateStatus(UUID userId, UserStatus requestedStatus, User actor) {
        if (requestedStatus != UserStatus.ACTIVE && requestedStatus != UserStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "INVALID_ACCOUNT_STATUS|Trang thai tai khoan chi co the la ACTIVE hoac LOCKED.");
        }

        User target = findUser(userId);
        UserStatus previousStatus = target.getStatus();

        if (previousStatus == requestedStatus) {
            return toResponse(target);
        }

        if (previousStatus != UserStatus.ACTIVE && previousStatus != UserStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION|Chi co the khoa tai khoan dang ACTIVE hoac mo tai khoan dang LOCKED.");
        }

        target.setStatus(requestedStatus);
        userRepository.saveAndFlush(target);

        if (requestedStatus == UserStatus.LOCKED) {
            refreshTokenRepository.revokeAllByUserId(target.getId());
        }

        auditService.log(
                actor != null ? actor.getId() : null,
                actor != null ? actor.getEmail() : null,
                requestedStatus == UserStatus.LOCKED ? "USER_ACCOUNT_LOCKED" : "USER_ACCOUNT_UNLOCKED",
                "USER",
                target.getId().toString(),
                previousStatus.name() + " -> " + requestedStatus.name()
        );

        return toResponse(target);
    }

    @Transactional
    public UserSuspensionActionResponse suspend(UUID userId, SuspendUserRequest request, User actor) {
        requireAdminActor(actor);

        String reason = normalizeSuspensionReason(request != null ? request.reason() : null);
        Integer durationDays = request != null ? request.durationDays() : null;
        validateDurationDays(durationDays);

        User target = findUserForUpdate(userId);
        validateSuspensionTarget(target, actor);

        UserStatus previousStatus = target.getStatus();
        if (previousStatus == UserStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "USER_ALREADY_SUSPENDED|Tai khoan da bi dinh chi.");
        }
        if (!isSuspendableStatus(target.getRole(), previousStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION|Trang thai hien tai khong the dinh chi tai khoan.");
        }

        Instant now = Instant.now();
        Instant suspensionUntil = durationDays != null ? now.plus(durationDays, ChronoUnit.DAYS) : null;

        target.setSuspensionPreviousStatus(previousStatus);
        target.setStatus(UserStatus.SUSPENDED);
        target.setSuspendedAt(now);
        target.setSuspendedBy(actor.getId());
        target.setSuspensionReason(reason);
        target.setSuspensionUntil(suspensionUntil);
        userRepository.saveAndFlush(target);

        refreshTokenRepository.revokeAllByUserId(target.getId());

        auditService.log(
                actor.getId(),
                actor.getEmail(),
                "USER_SUSPENDED",
                "USER",
                target.getId().toString(),
                "previous_status=" + previousStatus.name()
                        + "; new_status=" + UserStatus.SUSPENDED.name()
                        + "; duration_days=" + (durationDays != null ? durationDays : "indefinite")
        );

        return new UserSuspensionActionResponse(
                "Da dinh chi tai khoan",
                target.getId(),
                target.getStatus(),
                previousStatus,
                target.getSuspendedAt(),
                target.getSuspensionUntil());
    }

    @Transactional
    public UserSuspensionActionResponse activate(UUID userId, ActivateUserRequest request, User actor) {
        requireAdminActor(actor);
        validateActivationNote(request != null ? request.note() : null);

        User target = findUserForUpdate(userId);
        validateStaffTarget(target, actor);

        if (target.getStatus() != UserStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "USER_NOT_SUSPENDED|Tai khoan chua bi dinh chi.");
        }

        UserStatus restoredStatus = target.getSuspensionPreviousStatus();
        if (restoredStatus == null) {
            restoredStatus = UserStatus.ACTIVE;
        }
        if (!isSuspendableStatus(target.getRole(), restoredStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_REACTIVATION_STATE|Khong the xac dinh trang thai khoi phuc.");
        }

        target.setStatus(restoredStatus);
        target.setSuspensionPreviousStatus(null);
        target.setSuspendedAt(null);
        target.setSuspendedBy(null);
        target.setSuspensionReason(null);
        target.setSuspensionUntil(null);
        userRepository.saveAndFlush(target);

        auditService.log(
                actor.getId(),
                actor.getEmail(),
                "USER_REACTIVATED",
                "USER",
                target.getId().toString(),
                "previous_status=" + UserStatus.SUSPENDED.name()
                        + "; restored_status=" + restoredStatus.name()
        );

        return new UserSuspensionActionResponse(
                "Da kich hoat lai tai khoan",
                target.getId(),
                target.getStatus(),
                UserStatus.SUSPENDED,
                null,
                null);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND|Khong tim thay tai khoan."));
    }

    private User findUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND|Khong tim thay tai khoan."));
    }

    private void requireAdminActor(User actor) {
        if (actor == null || actor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN|Ban khong co quyen truy cap chuc nang nay.");
        }
    }

    private void validateSuspensionTarget(User target, User actor) {
        validateStaffTarget(target, actor);
    }

    private void validateStaffTarget(User target, User actor) {
        if (target.getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "CANNOT_SUSPEND_SELF|Khong the dinh chi chinh tai khoan cua ban.");
        }
        if (target.getRole() != UserRole.CUSTOMER && target.getRole() != UserRole.DRIVER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "STAFF_SUSPENSION_OUT_OF_SCOPE|Khong the dinh chi tai khoan nhan su tai day.");
        }
    }

    private boolean isSuspendableStatus(UserRole role, UserStatus status) {
        if (role == UserRole.CUSTOMER) {
            return CUSTOMER_SUSPENDABLE_STATUSES.contains(status);
        }
        if (role == UserRole.DRIVER) {
            return DRIVER_SUSPENDABLE_STATUSES.contains(status);
        }
        return false;
    }

    private String normalizeSuspensionReason(String reason) {
        String normalized = reason != null ? reason.strip() : "";
        if (normalized.length() < SUSPENSION_REASON_MIN_LENGTH
                || normalized.length() > SUSPENSION_REASON_MAX_LENGTH
                || !containsLetter(normalized)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_SUSPENSION_REASON|Ly do dinh chi khong hop le.");
        }
        return normalized;
    }

    private boolean containsLetter(String value) {
        return value.codePoints().anyMatch(Character::isLetter);
    }

    private void validateDurationDays(Integer durationDays) {
        if (durationDays == null) {
            return;
        }
        if (durationDays < SUSPENSION_DURATION_MIN_DAYS || durationDays > SUSPENSION_DURATION_MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_SUSPENSION_DURATION|Thoi han dinh chi khong hop le.");
        }
    }

    private void validateActivationNote(String note) {
        if (note != null && note.strip().length() > ACTIVATION_NOTE_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_ACTIVATION_NOTE|Ghi chu khoi phuc khong hop le.");
        }
    }

    private UserAccountStatusResponse toResponse(User user) {
        return new UserAccountStatusResponse(user.getId(), user.getRole(), user.getStatus());
    }
}
