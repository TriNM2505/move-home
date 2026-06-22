package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.UserAccountStatusResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.RefreshTokenRepository;
import vn.movehome.backend.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserAccountService {

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

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND|Khong tim thay tai khoan."));
    }

    private UserAccountStatusResponse toResponse(User user) {
        return new UserAccountStatusResponse(user.getId(), user.getRole(), user.getStatus());
    }
}
