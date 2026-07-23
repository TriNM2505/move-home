package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.dto.auth.AuthResponse;
import vn.movehome.backend.dto.auth.LoginRequest;
import vn.movehome.backend.dto.auth.RefreshRequest;
import vn.movehome.backend.dto.auth.RegisterCustomerRequest;
import vn.movehome.backend.dto.auth.RegisterDriverRequest;
import vn.movehome.backend.dto.auth.ResendVerificationRequest;
import vn.movehome.backend.dto.auth.VerifyEmailRequest;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.service.AuthService;
import vn.movehome.backend.service.PasswordResetService;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test cac endpoint con lai cua AuthController (register/verify-email/login/refresh/resend-verification).
 * forgot-password va reset-password da duoc kiem tra o AuthControllerPasswordResetTest.
 * Style: khoi tao truc tiep controller voi mock service (khong dung MockMvc), giong DriverDocumentControllerTest.
 */
class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);
    private final AuthController controller = new AuthController(authService, passwordResetService);

    @Test
    void registerCustomerDelegatesToAuthService() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "customer@movehome.vn", "Password@2026", "Nguyen Van A", "0912345678", true);
        AuthResponse expected = sampleAuthResponse();
        when(authService.registerCustomer(req)).thenReturn(expected);

        AuthResponse actual = controller.registerCustomer(req);

        assertThat(actual).isEqualTo(expected);
        verify(authService).registerCustomer(req);
    }

    @Test
    void registerDriverDelegatesToAuthService() {
        RegisterDriverRequest req = new RegisterDriverRequest(
                "driver@movehome.vn", "Password@2026", "Tran Van B", "0912345679", true);
        AuthResponse expected = sampleAuthResponse();
        when(authService.registerDriver(req)).thenReturn(expected);

        AuthResponse actual = controller.registerDriver(req);

        assertThat(actual).isEqualTo(expected);
        verify(authService).registerDriver(req);
    }

    @Test
    void verifyEmailDelegatesAndReturnsSuccessMessage() {
        VerifyEmailRequest req = new VerifyEmailRequest("raw-token");

        Map<String, String> response = controller.verifyEmail(req);

        verify(authService).verifyEmail(req);
        assertThat(response).containsEntry("message", "Email da xac thuc thanh cong. Ban co the dang nhap.");
    }

    @Test
    void loginDelegatesToAuthService() {
        LoginRequest req = new LoginRequest("customer@movehome.vn", "Password@2026");
        AuthResponse expected = sampleAuthResponse();
        when(authService.login(req)).thenReturn(expected);

        AuthResponse actual = controller.login(req);

        assertThat(actual).isEqualTo(expected);
        verify(authService).login(req);
    }

    @Test
    void refreshDelegatesToAuthService() {
        RefreshRequest req = new RefreshRequest("raw-refresh-token");
        AuthResponse expected = sampleAuthResponse();
        when(authService.refresh(req)).thenReturn(expected);

        AuthResponse actual = controller.refresh(req);

        assertThat(actual).isEqualTo(expected);
        verify(authService).refresh(req);
    }

    @Test
    void resendVerificationDelegatesAndReturnsNeutralMessage() {
        ResendVerificationRequest req = new ResendVerificationRequest("customer@movehome.vn");

        Map<String, String> response = controller.resendVerification(req);

        verify(authService).resendVerification(req);
        assertThat(response).containsEntry("message",
                "Nếu email tồn tại và chưa được xác thực, chúng tôi đã gửi lại liên kết xác thực.");
    }

    private AuthResponse sampleAuthResponse() {
        return new AuthResponse(
                "access-token",
                "refresh-token",
                "Bearer",
                900L,
                new AuthResponse.UserInfo(
                        UUID.randomUUID(),
                        "customer@movehome.vn",
                        "Nguyen Van A",
                        UserRole.CUSTOMER,
                        UserStatus.ACTIVE,
                        false,
                        null));
    }
}
