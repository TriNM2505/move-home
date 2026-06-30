package vn.movehome.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.movehome.backend.entity.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho bang refresh_token.
 * Khong co soft delete — token bi hard-delete sau khi het han > 30 ngay (NFR-06, Round 3 job).
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Tim refresh token theo SHA-256 hash.
     * Tra ve ca token da bi revoke de detect reuse attack (FR-029).
     * Caller phai kiem tra revokedAt va expiresAt sau khi nhan ket qua.
     *
     * @param tokenHash SHA-256 hex (64 ky tu) cua raw refresh token
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke tat ca refresh token chua bi revoke cua mot user (PANIC MODE — FR-029).
     * Goi khi phat hien ke tan cong dang reuse token da bi revoke cua na nhan.
     * @Modifying can phai kem @Transactional tren caller.
     */
    default void revokeAllByUserId(UUID userId) {
        revokeAllByUserIdAt(userId, Instant.now());
    }

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.userId = :userId AND r.revokedAt IS NULL")
    void revokeAllByUserIdAt(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    /**
     * Lay danh sach refresh token con hieu luc cua mot user.
     * Dung de hien thi active sessions va cho phep user revoke thu cong (Round 3).
     */
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
