package vn.movehome.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Token xac thuc email, sinh khi tao tai khoan Customer/Driver (FR-008).
 * Luu SHA-256 hash cua raw token gui trong email — KHONG luu raw token (tuong tu refresh token).
 * Het han sau 24 gio. Mot user chi nen co 1 token chua dung tai 1 thoi diem.
 */
@Entity
@Table(
    name = "email_verification_token",
    indexes = {
        @Index(name = "idx_evtoken_token", columnList = "token", unique = true),
        @Index(name = "idx_evtoken_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FK toi app_user.id — luu UUID thuan tuy de tranh lap the quan he JPA phuc tap
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // SHA-256 hex (64 ky tu) cua raw token gui trong link email (FR-008)
    // Client gui raw token → AuthService hash lai → tim bang truong nay
    @Column(nullable = false, unique = true, length = 100)
    private String token;

    // Token het han sau 24 gio ke tu khi tao (FR-008)
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Dau cham token da duoc su dung — NULL = chua verify, co gia tri = da verify
    // Chong reuse: neu usedAt != NULL → tra loi loi TOKEN_ALREADY_USED
    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
