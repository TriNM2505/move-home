package vn.movehome.backend.dto.auth;

import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;

import java.util.UUID;

/**
 * Response chung cho register, login, va refresh token (FR-023).
 * refreshToken tra trong body (Round 2 — demo don gian).
 * Round 3 se chuyen refreshToken sang httpOnly cookie (FR-026, AC-03).
 */
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,    // So giay het han cua access token (900 = 15 phut)
    UserInfo user
) {

    /**
     * Thong tin nguoi dung toi thieu — chi cung cap cai client can de hien thi UI.
     * KHONG tra password_hash, failed_login_count hay thong tin noi bo (HR-17).
     */
    public record UserInfo(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        UserStatus status,
        boolean mustChangePassword,  // Flag canh bao Staff phai doi password (FR-037)
        String avatarUrl             // URL anh dai dien Cloudinary — NULL neu chua upload
    ) {}
}
