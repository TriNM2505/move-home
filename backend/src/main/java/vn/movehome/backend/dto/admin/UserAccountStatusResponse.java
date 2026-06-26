package vn.movehome.backend.dto.admin;

import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;

import java.util.UUID;

public record UserAccountStatusResponse(
        UUID userId,
        UserRole role,
        UserStatus status
) {
}
