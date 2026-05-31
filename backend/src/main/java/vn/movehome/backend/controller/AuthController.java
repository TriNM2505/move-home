package vn.movehome.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.movehome.backend.dto.auth.*;
import vn.movehome.backend.service.AuthService;

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
}
