package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.auth.AuthResponse;
import vn.movehome.backend.dto.auth.RefreshRequest;
import vn.movehome.backend.dto.auth.RegisterCustomerRequest;
import vn.movehome.backend.email.notification.EmailService;
import vn.movehome.backend.entity.RefreshToken;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.EmailVerificationTokenRepository;
import vn.movehome.backend.repository.RefreshTokenRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.security.JwtTokenProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test cho AuthService — phan lam moi token (refresh) va normalizePhone.
 * Su dung MockitoExtension, KHONG dung SpringBootTest de dam bao chay nhanh.
 * Tham chieu: FR-028 (token rotation), FR-029 (panic mode reuse detection).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    // ===== HANG SO TEST =====

    private static final String RAW_REFRESH_TOKEN  = "raw-refresh-token";
    private static final String HASHED_TOKEN       = "hashed-refresh-token";
    private static final String NEW_RAW_REFRESH    = "new-raw-refresh-token";
    private static final String NEW_HASHED_TOKEN   = "new-hashed-refresh-token";
    private static final String ACCESS_TOKEN       = "access-token";

    // ===== MOCK DEPENDENCIES =====

    @Mock private UserRepository               userRepository;
    @Mock private EmailVerificationTokenRepository emailTokenRepository;
    @Mock private RefreshTokenRepository       refreshTokenRepository;
    @Mock private JwtTokenProvider             jwtTokenProvider;
    @Mock private PasswordEncoder              passwordEncoder;
    @Mock private LoginEventRecorder           loginEventRecorder;
    @Mock private EmailService                 emailService;
    @Mock private DriverProfileRepository      driverProfileRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Khoi tao AuthService voi cac mock dependency
        authService = new AuthService(
                userRepository,
                emailTokenRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                passwordEncoder,
                loginEventRecorder,
                emailService,
                driverProfileRepository,
                "http://localhost:5500/frontend/pages/verify-email-success.html");
    }

    // ===== HELPER: tao RefreshToken hop le (chua revoke, chua het han) =====

    private RefreshToken validStoredToken(UUID userId) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(HASHED_TOKEN)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS)) // con han
                .revokedAt(null) // chua bi revoke
                .build();
    }

    // ===== HELPER: tao User hop le =====

    private User activeUser(UUID userId) {
        return User.builder()
                .id(userId)
                .email("customer@movehome.vn")
                .fullName("Nguyen Van A")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .mustChangePassword(false)
                .build();
    }

    // =========================================================================
    // TEST 1: Happy path — token hop le, xoay vong thanh cong (FR-028)
    // =========================================================================

    /**
     * Kich ban: refresh token hop le, user ton tai.
     * Ket qua mong doi:
     * - Tra ve AuthResponse voi access token va refresh token moi.
     * - Token cu bi revoke va luu replacedByTokenId.
     * - Token moi duoc luu vao DB.
     */
    @Test
    void validTokenRotatesSuccessfully() {
        UUID userId     = UUID.randomUUID();
        UUID newTokenId = UUID.randomUUID();

        RefreshToken stored = validStoredToken(userId);
        User         user   = activeUser(userId);

        // Stub: hash raw token → tìm trong DB
        when(jwtTokenProvider.hashToken(RAW_REFRESH_TOKEN)).thenReturn(HASHED_TOKEN);
        when(refreshTokenRepository.findByTokenHash(HASHED_TOKEN)).thenReturn(Optional.of(stored));

        // Stub: tạo token mới
        when(jwtTokenProvider.generateRefreshToken()).thenReturn(NEW_RAW_REFRESH);
        when(jwtTokenProvider.hashToken(NEW_RAW_REFRESH)).thenReturn(NEW_HASHED_TOKEN);
        when(jwtTokenProvider.refreshTokenExpiry())
                .thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        // Stub: luu token moi tra ve entity co id de dat replacedByTokenId
        RefreshToken savedNewToken = RefreshToken.builder()
                .id(newTokenId)
                .userId(userId)
                .tokenHash(NEW_HASHED_TOKEN)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedNewToken);

        // Stub: tim user
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Stub: tao access token moi
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn(ACCESS_TOKEN);

        // Thuc thi
        AuthResponse response = authService.refresh(new RefreshRequest(RAW_REFRESH_TOKEN));

        // Kiem tra response tra ve token moi (FR-028)
        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.refreshToken()).isEqualTo(NEW_RAW_REFRESH);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().id()).isEqualTo(userId);

        // Kiem tra token cu da bi revoke (revokedAt != null) va co replacedByTokenId
        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(stored.getReplacedByTokenId()).isEqualTo(newTokenId);

        // Kiem tra refreshTokenRepository.save duoc goi 2 lan: 1 cho token moi, 1 cho token cu
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    // =========================================================================
    // TEST 2: PANIC MODE — token da bi revoke, phat hien reuse attack (FR-029)
    // =========================================================================

    /**
     * Kich ban: refresh token da co revokedAt (da dung truoc do).
     * Ket qua mong doi:
     * - revokeAllByUserId duoc goi de vo hieu tat ca phien cua user (PANIC MODE).
     * - Nem ResponseStatusException 401 voi code TOKEN_REUSE_DETECTED.
     */
    @Test
    void revokedTokenTriggersPanicMode() {
        UUID userId = UUID.randomUUID();

        // Token da bi revoke tu truoc (dau hieu ke tan cong tai su dung token cu)
        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(HASHED_TOKEN)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revokedAt(Instant.now().minus(1, ChronoUnit.HOURS)) // da bi revoke
                .build();

        when(jwtTokenProvider.hashToken(RAW_REFRESH_TOKEN)).thenReturn(HASHED_TOKEN);
        when(refreshTokenRepository.findByTokenHash(HASHED_TOKEN)).thenReturn(Optional.of(revokedToken));

        // Kiem tra nem 401 TOKEN_REUSE_DETECTED
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(RAW_REFRESH_TOKEN)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).startsWith("TOKEN_REUSE_DETECTED|");
                });

        // PANIC MODE: phai goi revokeAllByUserId de vo hieu toan bo phien
        verify(refreshTokenRepository).revokeAllByUserId(eq(userId));

        // Khong tao token moi
        verify(jwtTokenProvider, never()).generateRefreshToken();
    }

    // =========================================================================
    // TEST 3: Token het han — luu revokedAt roi nem 401 TOKEN_EXPIRED
    // =========================================================================

    /**
     * Kich ban: refresh token chua bi revoke nhung da het han (expiresAt trong qua khu).
     * Ket qua mong doi:
     * - revokedAt duoc set tren token va token duoc luu lai.
     * - Nem ResponseStatusException 401 voi code TOKEN_EXPIRED.
     */
    @Test
    void expiredTokenThrows401() {
        UUID userId = UUID.randomUUID();

        // Token het han tu 1 gio truoc, chua bi revoke
        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(HASHED_TOKEN)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // da het han
                .revokedAt(null) // chua bi revoke
                .build();

        when(jwtTokenProvider.hashToken(RAW_REFRESH_TOKEN)).thenReturn(HASHED_TOKEN);
        when(refreshTokenRepository.findByTokenHash(HASHED_TOKEN)).thenReturn(Optional.of(expiredToken));

        // Kiem tra nem 401 TOKEN_EXPIRED
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(RAW_REFRESH_TOKEN)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).startsWith("TOKEN_EXPIRED|");
                });

        // Token het han phai duoc danh dau revokedAt va luu lai (tranh reuse)
        assertThat(expiredToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(expiredToken);

        // Khong tao token moi
        verify(jwtTokenProvider, never()).generateRefreshToken();
    }

    // =========================================================================
    // TEST 4: Token khong ton tai trong DB — nem 401 INVALID_REFRESH_TOKEN
    // =========================================================================

    /**
     * Kich ban: hash cua token khong khop bat ky ban ghi nao trong DB.
     * Ket qua mong doi: Nem 401 INVALID_REFRESH_TOKEN ngay lap tuc.
     */
    @Test
    void unknownTokenThrows401() {
        when(jwtTokenProvider.hashToken(RAW_REFRESH_TOKEN)).thenReturn(HASHED_TOKEN);
        when(refreshTokenRepository.findByTokenHash(HASHED_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(RAW_REFRESH_TOKEN)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).startsWith("INVALID_REFRESH_TOKEN|");
                });

        // Khong truy cap DB user hay tao token moi
        verify(userRepository, never()).findById(any());
        verify(jwtTokenProvider, never()).generateRefreshToken();
    }

    // =========================================================================
    // TEST 5: Token hop le nhung user da bi xoa — nem 401 INVALID_REFRESH_TOKEN
    // =========================================================================

    /**
     * Kich ban: refresh token hop le, chua het han, nhung userId tuong ung khong con trong DB.
     * Ket qua mong doi: Nem 401 INVALID_REFRESH_TOKEN (tai khoan khong ton tai).
     */
    @Test
    void userNotFoundAfterValidTokenThrows401() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = validStoredToken(userId);

        when(jwtTokenProvider.hashToken(RAW_REFRESH_TOKEN)).thenReturn(HASHED_TOKEN);
        when(refreshTokenRepository.findByTokenHash(HASHED_TOKEN)).thenReturn(Optional.of(stored));
        // User khong con ton tai (co the da bi xoa sau khi token duoc phat hanh)
        // findById duoc goi TRUOC khi tao token moi — cac stub generateRefreshToken/save khong can thiet
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(RAW_REFRESH_TOKEN)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).startsWith("INVALID_REFRESH_TOKEN|");
                });

        // Khong phat hanh access token
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    // =========================================================================
    // TEST 6: normalizePhone — null → tra ve null
    // =========================================================================

    /**
     * Kiem tra normalizePhone qua registerCustomer (vi normalizePhone la private).
     * Kich ban: phone null trong RegisterCustomerRequest.
     * Ket qua mong doi: truong phone tren User duoc luu vao DB la null.
     */
    @Test
    void normalizePhoneNullReturnsNull() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "test@example.com", "Password@123", "Nguyen Van A",
                null, // phone = null
                true);

        stubRegisterCustomer(req.email());

        authService.registerCustomer(req);

        // Bat User duoc truyen vao userRepository.save de kiem tra phone
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPhone()).isNull();
    }

    // =========================================================================
    // TEST 7: normalizePhone — chuoi trong → tra ve null
    // =========================================================================

    /**
     * Kich ban: phone la chuoi rong "" trong RegisterCustomerRequest.
     * Ket qua mong doi: truong phone tren User duoc luu la null.
     */
    @Test
    void normalizePhoneBlankReturnsNull() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "blank@example.com", "Password@123", "Nguyen Van B",
                "", // phone rong
                true);

        stubRegisterCustomer(req.email());

        authService.registerCustomer(req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPhone()).isNull();
    }

    // =========================================================================
    // TEST 8: normalizePhone — da co dau +84 → giu nguyen (khong them +84 lan 2)
    // =========================================================================

    /**
     * Kich ban: phone "+84912345678" da o dang quoc te, bat dau bang '+' khong phai '0'.
     * Ket qua mong doi: phone duoc luu la "+84912345678" (khong bi them tien to +84 lan 2).
     */
    @Test
    void normalizePhoneAlreadyInternationalFormatReturnsTrimmed() {
        String internationalPhone = "+84912345678";
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "intl@example.com", "Password@123", "Nguyen Van C",
                internationalPhone,
                true);

        stubRegisterCustomer(req.email());

        authService.registerCustomer(req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        // normalizePhone: khong bat dau bang '0' → giu nguyen sau khi strip()
        assertThat(userCaptor.getValue().getPhone()).isEqualTo(internationalPhone);
    }

    // =========================================================================
    // HELPER: stub cac mock can thiet cho registerCustomer thanh cong
    // =========================================================================

    /**
     * Stub chung cho cac test normalizePhone:
     * - Email chua ton tai → co the dang ky.
     * - passwordEncoder, userRepository.save, jwtTokenProvider du de buildAuthResponse.
     * - emailTokenRepository du cho sendVerificationEmail.
     */
    private void stubRegisterCustomer(String email) {
        UUID userId = UUID.randomUUID();

        // Email chua bi dung
        when(userRepository.existsByEmail(email.toLowerCase())).thenReturn(false);

        // BCrypt encode password
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");

        // Tao user voi id sau khi save
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            // Gan id neu chua co (simulate DB auto-generate)
            if (u.getId() == null) {
                u = User.builder()
                        .id(userId)
                        .email(u.getEmail())
                        .passwordHash(u.getPasswordHash())
                        .fullName(u.getFullName())
                        .phone(u.getPhone())
                        .role(u.getRole())
                        .status(u.getStatus())
                        .emailVerified(u.isEmailVerified())
                        .mustChangePassword(u.isMustChangePassword())
                        .failedLoginCount(u.getFailedLoginCount())
                        .build();
            }
            return u;
        });

        // Stub cho sendVerificationEmail va buildAuthResponse:
        // hashToken duoc goi 2 lan: lan 1 cho email verification token (any UUID),
        // lan 2 cho refresh token hash (NEW_RAW_REFRESH) — dung any() de phu het ca hai
        when(jwtTokenProvider.hashToken(any()))
                .thenReturn("email-token-hash")  // lan 1: email verification
                .thenReturn("refresh-token-hash"); // lan 2: refresh token
        doNothing().when(emailTokenRepository).deleteByUserId(any());
        when(emailTokenRepository.save(any())).thenReturn(null);

        // Stub cho buildAuthResponse
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.generateRefreshToken()).thenReturn(NEW_RAW_REFRESH);
        when(jwtTokenProvider.refreshTokenExpiry())
                .thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));
        when(refreshTokenRepository.save(any())).thenReturn(null);
    }
}
