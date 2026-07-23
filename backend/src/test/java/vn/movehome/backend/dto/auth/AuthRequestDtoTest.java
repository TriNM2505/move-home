package vn.movehome.backend.dto.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cac request DTO trong dto/auth la record thuan tuy (khong logic) — test nay chi
 * dam bao constructor + toan bo accessor + equals/hashCode/toString duoc goi
 * it nhat 1 lan de dat coverage. Validation annotation (@NotBlank, @Pattern, ...)
 * duoc kiem tra o tang controller/integration, khong phai o day.
 */
class AuthRequestDtoTest {

    @Test
    void registerCustomerRequestExposesAllAccessors() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "customer@movehome.vn", "Password@2026", "Nguyen Van A", "0912345678", true);

        assertThat(req.email()).isEqualTo("customer@movehome.vn");
        assertThat(req.password()).isEqualTo("Password@2026");
        assertThat(req.fullName()).isEqualTo("Nguyen Van A");
        assertThat(req.phone()).isEqualTo("0912345678");
        assertThat(req.termsAccepted()).isTrue();
        assertThat(req).isEqualTo(req);
        assertThat(req.hashCode()).isEqualTo(req.hashCode());
        assertThat(req.toString()).contains("customer@movehome.vn");
    }

    @Test
    void registerDriverRequestExposesAllAccessors() {
        RegisterDriverRequest req = new RegisterDriverRequest(
                "driver@movehome.vn", "Password@2026", "Tran Van B", "+84912345679", true);

        assertThat(req.email()).isEqualTo("driver@movehome.vn");
        assertThat(req.password()).isEqualTo("Password@2026");
        assertThat(req.fullName()).isEqualTo("Tran Van B");
        assertThat(req.phone()).isEqualTo("+84912345679");
        assertThat(req.termsAccepted()).isTrue();
        assertThat(req).isEqualTo(req);
        assertThat(req.hashCode()).isEqualTo(req.hashCode());
        assertThat(req.toString()).contains("driver@movehome.vn");
    }

    @Test
    void refreshRequestExposesRefreshToken() {
        RefreshRequest req = new RefreshRequest("raw-refresh-token");

        assertThat(req.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(req).isEqualTo(req);
        assertThat(req.hashCode()).isEqualTo(req.hashCode());
        assertThat(req.toString()).contains("raw-refresh-token");
    }

    @Test
    void verifyEmailRequestExposesToken() {
        VerifyEmailRequest req = new VerifyEmailRequest("raw-verify-token");

        assertThat(req.token()).isEqualTo("raw-verify-token");
        assertThat(req).isEqualTo(req);
        assertThat(req.hashCode()).isEqualTo(req.hashCode());
        assertThat(req.toString()).contains("raw-verify-token");
    }

    @Test
    void resendVerificationRequestExposesEmail() {
        ResendVerificationRequest req = new ResendVerificationRequest("customer@movehome.vn");

        assertThat(req.email()).isEqualTo("customer@movehome.vn");
        assertThat(req).isEqualTo(req);
        assertThat(req.hashCode()).isEqualTo(req.hashCode());
        assertThat(req.toString()).contains("customer@movehome.vn");
    }
}
