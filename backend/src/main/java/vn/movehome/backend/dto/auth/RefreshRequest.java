package vn.movehome.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body xin cap lai access token bang refresh token (FR-028).
 * Round 2: refresh token gui trong body.
 * Round 3: se doc tu httpOnly cookie (FR-027 — "EXCLUSIVELY from cookie").
 */
public record RefreshRequest(

    @NotBlank(message = "Refresh token khong duoc de trong.")
    String refreshToken

) {}
