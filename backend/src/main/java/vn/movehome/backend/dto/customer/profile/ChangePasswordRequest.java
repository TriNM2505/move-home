package vn.movehome.backend.dto.customer.profile;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) {
}
