package vn.movehome.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.movehome.backend.dto.auth.ForgotPasswordRequest;
import vn.movehome.backend.dto.auth.ResetPasswordRequest;
import vn.movehome.backend.exception.GlobalExceptionHandler;
import vn.movehome.backend.service.AuthService;
import vn.movehome.backend.service.PasswordResetService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerPasswordResetTest {

    private static final String NEUTRAL_MESSAGE =
            "Nếu email tồn tại trong hệ thống, chúng tôi sẽ gửi hướng dẫn đặt lại mật khẩu.";

    @Mock
    private AuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, passwordResetService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void forgotPasswordEndpointReturnsNeutralMessage() throws Exception {
        when(passwordResetService.requestReset(any(ForgotPasswordRequest.class)))
                .thenReturn(NEUTRAL_MESSAGE);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@movehome.vn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(NEUTRAL_MESSAGE));

        verify(passwordResetService).requestReset(any(ForgotPasswordRequest.class));
    }

    @Test
    void resetPasswordEndpointAcceptsTokenAndNewPassword() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"raw-reset-token","newPassword":"NewPassword@2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Mật khẩu đã được đặt lại thành công. Bạn có thể đăng nhập."));

        verify(passwordResetService).resetPassword(any(ResetPasswordRequest.class));
    }
}
