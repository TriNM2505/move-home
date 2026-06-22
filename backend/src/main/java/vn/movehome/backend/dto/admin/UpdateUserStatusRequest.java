package vn.movehome.backend.dto.admin;

import jakarta.validation.constraints.NotNull;
import vn.movehome.backend.entity.UserStatus;

public record UpdateUserStatusRequest(
        @NotNull UserStatus status
) {
}
