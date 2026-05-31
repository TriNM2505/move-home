package vn.movehome.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token luu tren server de xac thuc phien lam viec dai han (AC-03).
 * Luu SHA-256 hash cua raw token — KHONG bao gio luu raw token trong DB (FR-025).
 * Token rotation: moi lan dung → tao token moi + revoke token cu → chain qua replacedByTokenId.
 * Phat hien reuse attack: neu token da bi revoke ma van duoc dung → PANIC MODE (FR-029).
 */
@Entity
@Table(
    name = "refresh_token",
    indexes = {
        @Index(name = "idx_rt_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_rt_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FK toi app_user.id — xoa cascade khi user bi xoa
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // SHA-256 hex (luon 64 ky tu) cua raw refresh token (FR-025)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // Token het han sau 7 ngay (AC-03)
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Set khi bi revoke (logout hoac rotation) — NULL nghia la token con hieu luc
    @Column(name = "revoked_at")
    private Instant revokedAt;

    // UUID cua token moi thay the sau khi rotation.
    // Dung detect reuse attack: neu token bi revoke co replacedByTokenId → ke xau co raw token cu (FR-029)
    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    // Thong tin device / browser — huu ich cho admin xem active sessions (Round 3)
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // IPv4 hoac IPv6, toi da 45 ky tu (vi du: 2001:0db8:85a3:0000:0000:8a2e:0370:7334)
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
