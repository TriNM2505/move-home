package vn.movehome.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import vn.movehome.backend.entity.EmailVerificationToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho bang email_verification_token.
 * Khong co soft delete — token bi xoa cung khi tao token moi hoac khi da verify (FR-008).
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /**
     * Tim token theo SHA-256 hash cua raw token.
     * Luong: User click link → FE gui raw token → BE hash → tim bang truong nay (FR-011).
     *
     * @param tokenHash SHA-256 hex string (64 ky tu)
     */
    Optional<EmailVerificationToken> findByToken(String tokenHash);

    /**
     * Xoa tat ca token verify email cua mot user (ke ca da dung).
     * Goi truoc khi tao token moi de dam bao moi user chi co 1 token tai 1 thoi diem (FR-008).
     * @Modifying can phai kem @Transactional tren caller.
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken e WHERE e.userId = :userId")
    void deleteByUserId(UUID userId);
}
