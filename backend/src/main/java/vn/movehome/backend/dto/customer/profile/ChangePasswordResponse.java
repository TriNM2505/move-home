package vn.movehome.backend.dto.customer.profile;

public record ChangePasswordResponse(
        String message,
        boolean forceRelogin,
        boolean sessionsRevoked
) {
}
