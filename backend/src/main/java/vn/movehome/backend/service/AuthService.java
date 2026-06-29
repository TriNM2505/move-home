package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.auth.*;
import vn.movehome.backend.entity.*;
import vn.movehome.backend.repository.*;
import vn.movehome.backend.security.JwtTokenProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Xu ly toan bo luong xac thuc: dang ky, xac thuc email, dang nhap, lam moi token.
 * Tuan thu: HR-02 (BCrypt), HR-16 (lockout), AC-03 (JWT rotation), FR-029 (panic mode).
 *
 * Format exception: ResponseStatusException(HttpStatus.XXX, "ERROR_CODE|Message tieng Viet.")
 * GlobalExceptionHandler se phan tach va tra JSON dung format ES-04.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    // Spec FR-031, HR-16: khoa 15 phut sau 5 lan sai
    private static final int LOCK_DURATION_MINUTES = 15;
    // Email token het han sau 24 gio (FR-008)
    private static final int EMAIL_TOKEN_EXPIRY_HOURS = 24;
    // Access token song 15 phut (tra ve expiresIn tinh bang giay)
    private static final long ACCESS_TOKEN_EXPIRY_SECONDS = 900L;

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    // ===== DANG KY CUSTOMER =====

    /**
     * Dang ky tai khoan Customer moi (FR-001..FR-010).
     * Tra ve AuthResponse voi token (Round 2 demo).
     * Token chi hoat dong sau khi xac thuc email (isEnabled() = false khi PENDING_VERIFY).
     */
    public AuthResponse registerCustomer(RegisterCustomerRequest req) {
        // Kiem tra email chua ton tai (FR-004)
        if (userRepository.existsByEmail(req.email().toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "CONFLICT|Email nay da duoc su dung. Vui long dung email khac.");
        }

        // Tao user moi (FR-007)
        User user = User.builder()
                .email(req.email().toLowerCase().strip())
                .passwordHash(passwordEncoder.encode(req.password())) // BCrypt cost 12 (FR-006)
                .fullName(req.fullName().strip())
                .phone(normalizePhone(req.phone()))
                .role(UserRole.CUSTOMER)
                .status(UserStatus.PENDING_VERIFY)
                .emailVerified(false)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .build();
        userRepository.save(user);
        log.info("Tai khoan Customer moi: userId={}, email={}", user.getId(), user.getEmail());

        // Tao email verification token (FR-008)
        String rawToken = sendVerificationEmail(user);

        // TODO Round 3: thay log bang gui email that qua Gmail SMTP (HR-11)
        log.info("[DEMO] Link xac thuc email: POST /api/auth/verify-email voi token={}", rawToken);

        // Tao tokens va tra response (Round 2 demo: token trong body)
        return buildAuthResponse(user, req.email());
    }

    // ===== XAC THUC EMAIL =====

    /**
     * Xac thuc email bang token tu link (FR-011..FR-014).
     * Customer: PENDING_VERIFY → ACTIVE.
     * Driver: PENDING_VERIFY → PENDING_DOCUMENTS.
     */
    public void verifyEmail(VerifyEmailRequest req) {
        String tokenHash = jwtTokenProvider.hashToken(req.token());

        EmailVerificationToken evToken = emailTokenRepository.findByToken(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "TOKEN_NOT_FOUND|Link xac thuc khong hop le hoac da het han."));

        // Kiem tra het han (FR-013)
        if (evToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                "TOKEN_EXPIRED|Link xac thuc da het han. Vui long yeu cau gui lai.");
        }

        // Kiem tra da dung (chong reuse)
        if (evToken.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "TOKEN_ALREADY_USED|Token nay da duoc su dung. Vui long dang nhap hoac yeu cau gui lai link.");
        }

        // Lay user va cap nhat trang thai (FR-014)
        User user = userRepository.findById(evToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "NOT_FOUND|Tai khoan khong ton tai."));

        // Xac dinh trang thai moi theo role: Customer → ACTIVE, Driver → PENDING_DOCUMENTS
        if (user.getRole() == UserRole.CUSTOMER) {
            user.setStatus(UserStatus.ACTIVE);
        } else if (user.getRole() == UserRole.DRIVER) {
            user.setStatus(UserStatus.PENDING_DOCUMENTS);
        }
        user.setEmailVerified(true);
        userRepository.save(user);

        // Danh dau token da dung (chong click link lan 2)
        evToken.setUsedAt(Instant.now());
        emailTokenRepository.save(evToken);

        log.info("Email da xac thuc: userId={}, role={}, newStatus={}",
                user.getId(), user.getRole(), user.getStatus());
    }

    // ===== DANG NHAP =====

    /**
     * Dang nhap bang email + password (FR-017..FR-023).
     * Kiem tra: khoa, password, status, roi moi cap token.
     * KHONG tiet lo nguoi dung co ton tai hay khong khi tra loi loi (chong enumeration FR-019).
     */
    public AuthResponse login(LoginRequest req) {
        String email = req.email().toLowerCase().strip();

        // Tim user (FR-019: neu khong tim thay → tra 401 giong nhu sai password)
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS|Ten dang nhap hoac mat khau khong dung."));

        // Khoa thu cong boi Admin la vo thoi han va khong duoc cap bat ky token nao.
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                "ACCOUNT_LOCKED|Tai khoan da bi khoa. Vui long lien he quan tri vien.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "ACCOUNT_SUSPENDED|Tai khoan da bi dinh chi. Vui long lien he quan tri vien.");
        }

        // Kiem tra khoa tam thoi do dang nhap sai truoc khi verify password (FR-021, FR-032)
        if (!user.isAccountNonLocked()) {
            long minutesLeft = ChronoUnit.MINUTES.between(Instant.now(), user.getLockedUntil()) + 1;
            throw new ResponseStatusException(HttpStatus.LOCKED,
                "ACCOUNT_LOCKED|Tai khoan bi khoa tam thoi. Vui long thu lai sau " + minutesLeft + " phut.");
        }

        // Kiem tra password (FR-022)
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS|Ten dang nhap hoac mat khau khong dung.");
        }

        // Password đúng → chỉ yêu cầu email đã xác thực; trạng thái onboarding không chặn login.
        checkUserStatusForLogin(user);

        // Reset failed login count (FR-033)
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("Dang nhap thanh cong: userId={}, email={}", user.getId(), email);
        return buildAuthResponse(user, email);
    }

    // ===== LAM MOI TOKEN =====

    /**
     * Xin cap lai access token bang refresh token (FR-028, FR-029).
     * Thuc hien token rotation: revoke token cu, phat hanh token moi.
     * Phat hien reuse attack (PANIC MODE): revoke tat ca token cua user.
     */
    public AuthResponse refresh(RefreshRequest req) {
        String tokenHash = jwtTokenProvider.hashToken(req.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "INVALID_REFRESH_TOKEN|Refresh token khong hop le. Vui long dang nhap lai."));

        // PANIC MODE: token da bi revoke ma van duoc dung → ke tan cong co raw token (FR-029)
        if (stored.getRevokedAt() != null) {
            log.warn("PANIC: Phat hien reuse refresh token da bi revoke! userId={}", stored.getUserId());
            refreshTokenRepository.revokeAllByUserId(stored.getUserId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "TOKEN_REUSE_DETECTED|Phien lam viec bi nghi ngo. Tat ca phien da bi vo hieu. Vui long dang nhap lai.");
        }

        // Token het han
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevokedAt(Instant.now());
            refreshTokenRepository.save(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "TOKEN_EXPIRED|Phien lam viec da het han. Vui long dang nhap lai.");
        }

        // Lay user
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "INVALID_REFRESH_TOKEN|Tai khoan khong ton tai."));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "ACCOUNT_LOCKED|Tai khoan da bi khoa. Vui long lien he quan tri vien.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "ACCOUNT_SUSPENDED|Tai khoan da bi dinh chi. Vui long lien he quan tri vien.");
        }

        // Tao token moi
        String newRawRefreshToken = jwtTokenProvider.generateRefreshToken();
        String newHash = jwtTokenProvider.hashToken(newRawRefreshToken);

        RefreshToken newToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(newHash)
                .expiresAt(jwtTokenProvider.refreshTokenExpiry())
                .build();
        newToken = refreshTokenRepository.save(newToken);

        // Rotation: revoke token cu, luu chain (FR-028)
        stored.setRevokedAt(Instant.now());
        stored.setReplacedByTokenId(newToken.getId());
        refreshTokenRepository.save(stored);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        log.debug("Token rotation thanh cong: userId={}", user.getId());

        return new AuthResponse(
            newAccessToken,
            newRawRefreshToken,
            "Bearer",
            ACCESS_TOKEN_EXPIRY_SECONDS,
            toUserInfo(user)
        );
    }

    // ===== PRIVATE HELPERS =====

    /**
     * Tang failedLoginCount; khoa tai khoan neu dat nguong (HR-16, FR-031).
     * Goi khi password sai. User se duoc save sau khi method nay chay.
     */
    private void handleFailedLogin(User user) {
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
        user.setLastFailedLoginAt(Instant.now());

        if (user.getFailedLoginCount() >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.LOCKED,
                "ACCOUNT_LOCKED_NOW|Tai khoan bi khoa 15 phut do nhap sai mat khau qua nhieu lan.");
        }
        userRepository.save(user);
    }

    /** Chỉ chặn tài khoản chưa xác thực email; trạng thái onboarding được xử lý tại endpoint nghiệp vụ. */
    private void checkUserStatusForLogin(User user) {
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "EMAIL_NOT_VERIFIED|Vui lòng xác thực email trước khi đăng nhập.");
        }
    }

    /**
     * Tao email verification token va luu DB.
     * Xoa token cu truoc khi tao moi (FR-008).
     *
     * @return rawToken (de log cho demo; Round 3 se gui qua email that)
     */
    private String sendVerificationEmail(User user) {
        // Xoa token cu cua user nay (neu co) de dam bao chi co 1 token hieu luc (FR-008)
        emailTokenRepository.deleteByUserId(user.getId());

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = jwtTokenProvider.hashToken(rawToken);

        EmailVerificationToken evToken = EmailVerificationToken.builder()
                .userId(user.getId())
                .token(tokenHash)
                .expiresAt(Instant.now().plus(EMAIL_TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS))
                .build();
        emailTokenRepository.save(evToken);

        return rawToken;
    }

    /**
     * Tao AuthResponse voi access token + refresh token moi.
     * Luu refresh token hash vao DB (FR-025).
     */
    private AuthResponse buildAuthResponse(User user, String email) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String rawRefreshToken = jwtTokenProvider.generateRefreshToken();
        String refreshHash = jwtTokenProvider.hashToken(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(refreshHash)
                .expiresAt(jwtTokenProvider.refreshTokenExpiry())
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
            accessToken,
            rawRefreshToken,
            "Bearer",
            ACCESS_TOKEN_EXPIRY_SECONDS,
            toUserInfo(user)
        );
    }

    /** Tao UserInfo DTO tu User entity (KHONG include du lieu noi bo). */
    private AuthResponse.UserInfo toUserInfo(User user) {
        return new AuthResponse.UserInfo(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getStatus(),
            user.isMustChangePassword()
        );
    }

    /** Chuan hoa so dien thoai: 0xxxxxxxxx → +84xxxxxxxxx. */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String trimmed = phone.strip();
        if (trimmed.startsWith("0")) {
            return "+84" + trimmed.substring(1);
        }
        return trimmed;
    }
}
