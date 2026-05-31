package vn.movehome.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.movehome.backend.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Component xu ly JWT: tao, xac thuc access token, tao va hash refresh token.
 * Dung jjwt 0.12.x. Secret doc tu bien moi truong JWT_SECRET (HR-01).
 * KHONG bao gio log full token — chi log prefix 8 ky tu de tranh leak.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    // Secret phai la Base64-encoded chuoi >= 32 byte (cho HS256)
    @Value("${app.jwt.secret}")
    private String secret;

    // Thoi gian song cua access token tinh bang phut (mac dinh 15 phut — AC-03)
    @Value("${app.jwt.access-expiry-minutes:15}")
    private int accessExpiryMinutes;

    // Thoi gian song cua refresh token tinh bang ngay (mac dinh 7 ngay — AC-03)
    @Value("${app.jwt.refresh-expiry-days:7}")
    private int refreshExpiryDays;

    // ===== ACCESS TOKEN =====

    /**
     * Sinh access token JWT (HS256).
     * Payload: sub=userId, role, email, iat, exp.
     * Token khong chua password, phone, hoac thong tin nhay cam khac (FR-024).
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessExpiryMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Xac thuc access token va tra ve userId neu hop le.
     * Tra Optional.empty() khi token het han, sai chu ky, hoac bi bien doi.
     * KHONG nem exception — caller xu ly Optional.empty() nhu token invalid.
     */
    public Optional<UUID> validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (ExpiredJwtException e) {
            // Token het han — binh thuong, client can goi refresh
            log.debug("Access token het han: {}...", truncate(token));
            return Optional.empty();
        } catch (JwtException e) {
            // Token bi bien doi hoac sai chu ky — canh bao
            log.warn("Access token khong hop le ({}): {}...", e.getClass().getSimpleName(), truncate(token));
            return Optional.empty();
        }
    }

    // ===== REFRESH TOKEN =====

    /**
     * Sinh raw refresh token: 32 byte ngau nhien → Base64URL encode (khong padding).
     * Ket qua la chuoi 43 ky tu. Client giu raw token; server chi luu hash (FR-025).
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Tinh thoi diem het han cua refresh token (now + refreshExpiryDays ngay).
     * Dung khi tao RefreshToken entity de set expiresAt.
     */
    public Instant refreshTokenExpiry() {
        return Instant.now().plus(refreshExpiryDays, ChronoUnit.DAYS);
    }

    // ===== HASHING =====

    /**
     * Tinh SHA-256 hex cua mot chuoi (raw token).
     * Dung cho: refresh token (truoc khi luu DB), email verification token.
     * Ket qua luon 64 ky tu hex.
     *
     * @param rawToken chuoi can hash (raw token chua qua xu ly)
     * @return SHA-256 hex string (64 ky tu)
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 la thuat toan bat buoc trong Java — khong the xay ra
            throw new IllegalStateException("SHA-256 khong duoc ho tro tren JVM nay", e);
        }
    }

    // ===== PRIVATE HELPERS =====

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Lay 8 ky tu dau cua token de log (tranh lo toan bo token). */
    private String truncate(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 8);
    }
}
