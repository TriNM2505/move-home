package vn.movehome.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.movehome.backend.dto.auth.*;
import vn.movehome.backend.service.AuthService;
import vn.movehome.backend.service.PasswordResetService;

import java.util.Map;

/**
 * Controller xac thuc — prefix /api/auth/** (permitAll trong SecurityConfig, HR-17).
 * Thin layer: validate → delegate sang AuthService.
 * KHONG xu ly business logic o day — moi logic nam trong AuthService.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    /**
     * Dang ky tai khoan Customer moi (FR-001).
     * Tra HTTP 201 Created voi AuthResponse.
     * Token trong response chi hoat dong sau khi xac thuc email.
     */
    @PostMapping("/register/customer")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerCustomer(@Valid @RequestBody RegisterCustomerRequest req) {
        return authService.registerCustomer(req);
    }

    /**
     * Dang ky tai khoan Driver moi (CONTEXT §2 Onboarding Buoc 1).
     * Tao User role=DRIVER + driver_profile rong. Sau xac thuc email → PENDING_DOCUMENTS.
     * Token trong response chi hoat dong sau khi xac thuc email.
     */
    @PostMapping("/register/driver")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerDriver(@Valid @RequestBody RegisterDriverRequest req) {
        return authService.registerDriver(req);
    }

    /**
     * Xac thuc email bang token tu link (FR-011).
     * Tra HTTP 200 voi message xac nhan.
     * Customer: status → ACTIVE. Driver: status → PENDING_DOCUMENTS.
     */
    @PostMapping("/verify-email")
    public Map<String, String> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        authService.verifyEmail(req);
        return Map.of("message", "Email da xac thuc thanh cong. Ban co the dang nhap.");
    }

    /**
     * Dang nhap bang email + password (FR-017).
     * Tra HTTP 200 voi AuthResponse (access + refresh token).
     * Cac truong hop loi: 401 sai password, 423 bi khoa, 403 chua verify/onboarding.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /**
     * Lam moi access token bang refresh token (FR-028).
     * Thuc hien token rotation — refresh token cu bi revoke, cap token moi.
     * Round 2: refresh token trong body. Round 3: se chuyen sang httpOnly cookie (FR-027).
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    /**
     * Gui lai email xac thuc cho tai khoan chua verify (FR-011, token cu het han 24h).
     * Luon tra ve message trung tinh de chong user enumeration (FR-019).
     */
    @PostMapping("/resend-verification")
    public Map<String, String> resendVerification(@Valid @RequestBody ResendVerificationRequest req) {
        authService.resendVerification(req);
        return Map.of("message",
                "Nếu email tồn tại và chưa được xác thực, chúng tôi đã gửi lại liên kết xác thực.");
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        return Map.of("message", passwordResetService.requestReset(req));
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req);
        return Map.of("message", "Mật khẩu đã được đặt lại thành công. Bạn có thể đăng nhập.");
    }
}
