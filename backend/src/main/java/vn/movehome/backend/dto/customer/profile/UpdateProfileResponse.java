package vn.movehome.backend.dto.customer.profile;

public record UpdateProfileResponse(
        CustomerProfileResponse profile,
        String message
) {
}
