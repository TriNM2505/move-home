package vn.movehome.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body xac thuc email (FR-011).
 * token: raw token nhan tu link trong email.
 * AuthService se hash lai de tim trong DB (bao mat: DB chi luu hash).
 */
public record VerifyEmailRequest(

    @NotBlank(message = "Token xac thuc khong duoc de trong.")
    String token

) {}
