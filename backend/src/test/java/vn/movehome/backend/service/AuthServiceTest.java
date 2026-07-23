package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.auth.AuthResponse;
import vn.movehome.backend.dto.auth.LoginRequest;
import vn.movehome.backend.dto.auth.RefreshRequest;
import vn.movehome.backend.dto.auth.RegisterCustomerRequest;
import vn.movehome.backend.dto.auth.RegisterDriverRequest;
import vn.movehome.backend.dto.auth.ResendVerificationRequest;
import vn.movehome.backend.dto.auth.VerifyEmailRequest;
import vn.movehome.backend.email.notification.EmailService;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.EmailVerificationToken;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "Admin@2026";
    private static final String PASSWORD_HASH = "bcrypt-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository emailTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginEventRecorder loginEventRecorder;

    @Mock
    private EmailService emailService;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    private static final String VERIFY_EMAIL_URL =
            "http://localhost:5500/frontend/pages/verify-email-success.html";

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                emailTokenRepository,
                refreshTokenRepository,
                jwtTokenProvider,
                passwordEncoder,
                loginEventRecorder,
                emailService,
                driverProfileRepository,
                VERIFY_EMAIL_URL);
    }

    @ParameterizedTest
    @MethodSource("verifiedLoginCases")
    void verifiedUsersReceiveTokenRegardlessOfDriverApprovalStatus(
            String email,
            UserRole role,
            UserStatus status
    ) {
        User user = verifiedUser(email, role, status);
        stubSuccessfulLogin(user);

        AuthResponse response = authService.login(new LoginRequest(email, PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().status()).isEqualTo(status);
        verify(userRepository).save(user);
        verify(loginEventRecorder).recordSuccessfulLogin(user.getId());
    }

    @Test
    void unverifiedUserStillCannotLogin() {
        String email = "driver-unverified@movehome.vn";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(PASSWORD_HASH)
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_VERIFY)
                .emailVerified(false)
                .build();
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).isEqualTo(
                            "EMAIL_NOT_VERIFIED|Vui lòng xác thực email trước khi đăng nhập.");
                });

        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(loginEventRecorder, never()).recordSuccessfulLogin(any());
    }

    @Test
    void adminLockedUserCannotLoginAndReceivesAccountLocked() {
        String email = "locked-customer@movehome.vn";
        User user = verifiedUser(email, UserRole.CUSTOMER, UserStatus.LOCKED);
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
                    assertThat(ex.getReason()).isEqualTo(
                            "ACCOUNT_LOCKED|Tai khoan da bi khoa. Vui long lien he quan tri vien.");
                });

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any());
        verify(loginEventRecorder, never()).recordSuccessfulLogin(any());
    }

    @Test
    void suspendedUserCannotLoginAndDoesNotReceiveTokens() {
        String email = "suspended-customer@movehome.vn";
        User user = verifiedUser(email, UserRole.CUSTOMER, UserStatus.SUSPENDED);
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).isEqualTo(
                            "ACCOUNT_SUSPENDED|Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
                });

        assertThat(user.isAccountNonLocked()).isFalse();
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void suspendedUserWithCustomReasonReceivesReasonInErrorDetail() {
        String email = "suspended-with-reason@movehome.vn";
        User user = verifiedUser(email, UserRole.CUSTOMER, UserStatus.SUSPENDED);
        user.setSuspensionReason("Thieu tien coc dong phat 200.000 d");
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).isEqualTo(
                            "ACCOUNT_SUSPENDED|Tài khoản đã bị khóa. Thieu tien coc dong phat 200.000 d");
                });

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void loginSucceedsEvenWhenRecordingLoginEventThrows() {
        String email = "recorder-throws@movehome.vn";
        User user = verifiedUser(email, UserRole.CUSTOMER, UserStatus.ACTIVE);
        stubSuccessfulLogin(user);
        org.mockito.Mockito.doThrow(new RuntimeException("Hang doi khong kha dung"))
                .when(loginEventRecorder).recordSuccessfulLogin(user.getId());

        AuthResponse response = authService.login(new LoginRequest(email, PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(loginEventRecorder).recordSuccessfulLogin(user.getId());
    }

    // ===== DANG KY CUSTOMER =====

    @Test
    void registerCustomerCreatesUserAndSendsVerificationEmailAndReturnsTokens() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "New.Customer@MoveHome.vn", "Password@2026", "  Nguyen Van A  ", "0912345678", true);
        when(userRepository.existsByEmail("new.customer@movehome.vn")).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn(PASSWORD_HASH);
        when(jwtTokenProvider.hashToken(anyString())).thenReturn("hashed-value");
        when(jwtTokenProvider.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.refreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        AuthResponse response = authService.registerCustomer(req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("new.customer@movehome.vn");
        assertThat(savedUser.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(savedUser.getPhone()).isEqualTo("+84912345678");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_VERIFY);
        assertThat(savedUser.isEmailVerified()).isFalse();

        verify(emailTokenRepository).deleteByUserId(savedUser.getId());
        verify(emailTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).send(eq("new.customer@movehome.vn"), eq("Xác thực email Move_home"), anyString());
        verifyNoInteractions(driverProfileRepository);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("new.customer@movehome.vn");
    }

    @Test
    void registerCustomerWithExistingEmailThrowsConflict() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "existing@movehome.vn", "Password@2026", "Nguyen Van A", null, true);
        when(userRepository.existsByEmail("existing@movehome.vn")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerCustomer(req))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "CONFLICT|Email nay da duoc su dung. Vui long dung email khac.");
                });

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService, emailTokenRepository);
    }

    @Test
    void registerCustomerWithNullPhoneKeepsPhoneNull() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "no-phone@movehome.vn", "Password@2026", "Nguyen Van B", null, true);
        when(userRepository.existsByEmail("no-phone@movehome.vn")).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn(PASSWORD_HASH);
        when(jwtTokenProvider.hashToken(anyString())).thenReturn("hashed-value");
        when(jwtTokenProvider.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.refreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        authService.registerCustomer(req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPhone()).isNull();
    }

    @Test
    void registerCustomerWithAlreadyInternationalPhoneKeepsItUnchanged() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "intl-phone@movehome.vn", "Password@2026", "Nguyen Van C", "+84912345678", true);
        when(userRepository.existsByEmail("intl-phone@movehome.vn")).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn(PASSWORD_HASH);
        when(jwtTokenProvider.hashToken(anyString())).thenReturn("hashed-value");
        when(jwtTokenProvider.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.refreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        authService.registerCustomer(req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPhone()).isEqualTo("+84912345678");
    }

    @Test
    void registerCustomerSendsVerificationEmailOnlyAfterTransactionCommitsWhenSynchronizationActive() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "sync-active@movehome.vn", "Password@2026", "Nguyen Van D", null, true);
        when(userRepository.existsByEmail("sync-active@movehome.vn")).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn(PASSWORD_HASH);
        when(jwtTokenProvider.hashToken(anyString())).thenReturn("hashed-value");
        when(jwtTokenProvider.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.refreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        TransactionSynchronizationManager.initSynchronization();
        try {
            authService.registerCustomer(req);

            // Chua commit thi email chua duoc gui (HR-11)
            verifyNoInteractions(emailService);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

            verify(emailService).send(eq("sync-active@movehome.vn"), eq("Xác thực email Move_home"), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ===== DANG KY DRIVER =====

    @Test
    void registerDriverCreatesUserAndEmptyDriverProfileAndSendsVerificationEmail() {
        RegisterDriverRequest req = new RegisterDriverRequest(
                "new.driver@MoveHome.vn", "Password@2026", "Tran Van B", "0987654321", true);
        when(userRepository.existsByEmail("new.driver@movehome.vn")).thenReturn(false);
        when(passwordEncoder.encode(req.password())).thenReturn(PASSWORD_HASH);
        when(jwtTokenProvider.hashToken(anyString())).thenReturn("hashed-value");
        when(jwtTokenProvider.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.refreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        AuthResponse response = authService.registerDriver(req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("new.driver@movehome.vn");
        assertThat(savedUser.getPhone()).isEqualTo("+84987654321");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.DRIVER);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_VERIFY);

        ArgumentCaptor<DriverProfile> profileCaptor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(driverProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getUserId()).isEqualTo(savedUser.getId());

        verify(emailService).send(eq("new.driver@movehome.vn"), eq("Xác thực email Move_home"), anyString());
        assertThat(response.user().email()).isEqualTo("new.driver@movehome.vn");
    }

    @Test
    void registerDriverWithExistingEmailThrowsConflict() {
        RegisterDriverRequest req = new RegisterDriverRequest(
                "existing-driver@movehome.vn", "Password@2026", "Tran Van B", null, true);
        when(userRepository.existsByEmail("existing-driver@movehome.vn")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerDriver(req))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "CONFLICT|Email nay da duoc su dung. Vui long dung email khac.");
                });

        verify(userRepository, never()).save(any());
        verifyNoInteractions(driverProfileRepository, emailService);
    }

    // ===== XAC THUC EMAIL =====

    @Test
    void verifyEmailActivatesCustomerAccount() {
        UUID userId = UUID.randomUUID();
        EmailVerificationToken evToken = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .token("hashed-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        User user = User.builder()
                .id(userId)
                .email("customer@movehome.vn")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.PENDING_VERIFY)
                .emailVerified(false)
                .build();
        when(jwtTokenProvider.hashToken("raw-verify-token")).thenReturn("hashed-token");
        when(emailTokenRepository.findByToken("hashed-token")).thenReturn(Optional.of(evToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.verifyEmail(new VerifyEmailRequest("raw-verify-token"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(evToken.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(emailTokenRepository).save(evToken);
    }

    @Test
    void verifyEmailMovesDriverToPendingDocuments() {
        UUID userId = UUID.randomUUID();
        EmailVerificationToken evToken = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .token("hashed-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        User user = User.builder()
                .id(userId)
                .email("driver@movehome.vn")
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_VERIFY)
                .emailVerified(false)
                .build();
        when(jwtTokenProvider.hashToken("raw-verify-token")).thenReturn("hashed-token");
        when(emailTokenRepository.findByToken("hashed-token")).thenReturn(Optional.of(evToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.verifyEmail(new VerifyEmailRequest("raw-verify-token"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_DOCUMENTS);
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void verifyEmailWithUnknownTokenThrowsNotFound() {
        when(jwtTokenProvider.hashToken("bad-token")).thenReturn("hashed-bad-token");
        when(emailTokenRepository.findByToken("hashed-bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("bad-token")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo(
                            "TOKEN_NOT_FOUND|Link xac thuc khong hop le hoac da het han.");
                });

        verify(userRepository, never()).findById(any());
    }

    @Test
    void verifyEmailWithExpiredTokenThrowsGone() {
        EmailVerificationToken evToken = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .token("hashed-token")
                .expiresAt(Instant.now().minus(1, ChronoUnit.SECONDS))
                .build();
        when(jwtTokenProvider.hashToken("expired-token")).thenReturn("hashed-token");
        when(emailTokenRepository.findByToken("hashed-token")).thenReturn(Optional.of(evToken));

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("expired-token")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.GONE);
                    assertThat(ex.getReason()).isEqualTo(
                            "TOKEN_EXPIRED|Link xac thuc da het han. Vui long yeu cau gui lai.");
                });

        verify(userRepository, never()).findById(any());
    }

    @Test
    void verifyEmailWithAlreadyUsedTokenThrowsConflict() {
        EmailVerificationToken evToken = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .token("hashed-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .usedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();
        when(jwtTokenProvider.hashToken("used-token")).thenReturn("hashed-token");
        when(emailTokenRepository.findByToken("hashed-token")).thenReturn(Optional.of(evToken));

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("used-token")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "TOKEN_ALREADY_USED|Token nay da duoc su dung. Vui long dang nhap hoac yeu cau gui lai link.");
                });

        verify(userRepository, never()).findById(any());
    }

    @Test
    void verifyEmailWithMissingUserThrowsNotFound() {
        UUID userId = UUID.randomUUID();
        EmailVerificationToken evToken = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .token("hashed-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        when(jwtTokenProvider.hashToken("orphan-token")).thenReturn("hashed-token");
        when(emailTokenRepository.findByToken("hashed-token")).thenReturn(Optional.of(evToken));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("orphan-token")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("NOT_FOUND|Tai khoan khong ton tai.");
                });

        verify(emailTokenRepository, never()).save(any());
    }

    // ===== GUI LAI EMAIL XAC THUC =====

    @Test
    void resendVerificationForUnknownEmailIsNoOp() {
        when(userRepository.findByEmailAndDeletedAtIsNull("unknown@movehome.vn"))
                .thenReturn(Optional.empty());

        authService.resendVerification(new ResendVerificationRequest("unknown@movehome.vn"));

        verifyNoInteractions(emailTokenRepository, emailService);
    }

    @Test
    void resendVerificationForAlreadyVerifiedUserIsNoOp() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("verified@movehome.vn")
                .emailVerified(true)
                .build();
        when(userRepository.findByEmailAndDeletedAtIsNull("verified@movehome.vn"))
                .thenReturn(Optional.of(user));

        authService.resendVerification(new ResendVerificationRequest("verified@movehome.vn"));

        verifyNoInteractions(emailTokenRepository, emailService);
    }

    @Test
    void resendVerificationForUnverifiedUserSendsNewToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("unverified@movehome.vn")
                .emailVerified(false)
                .build();
        when(userRepository.findByEmailAndDeletedAtIsNull("unverified@movehome.vn"))
                .thenReturn(Optional.of(user));
        when(jwtTokenProvider.hashToken(anyString())).thenReturn("hashed-value");

        authService.resendVerification(new ResendVerificationRequest("unverified@movehome.vn"));

        verify(emailTokenRepository).deleteByUserId(user.getId());
        verify(emailTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).send(eq("unverified@movehome.vn"), eq("Xác thực email Move_home"), anyString());
    }

    // ===== DANG NHAP — CAC NHANH CON LAI =====

    @Test
    void loginWithUnknownEmailThrowsUnauthorizedInvalidCredentials() {
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@movehome.vn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@movehome.vn", PASSWORD)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_CREDENTIALS|Ten dang nhap hoac mat khau khong dung.");
                });

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginWhileTemporarilyLockedThrowsLockedWithMinutesLeft() {
        String email = "temp-locked@movehome.vn";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(PASSWORD_HASH)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .failedLoginCount(5)
                .lockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, PASSWORD)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
                    assertThat(ex.getReason()).startsWith("ACCOUNT_LOCKED|Tai khoan bi khoa tam thoi.");
                    assertThat(ex.getReason()).contains("phut");
                });

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void loginWithWrongPasswordIncrementsFailedCountWithoutLockingBelowThreshold() {
        String email = "wrong-password@movehome.vn";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(PASSWORD_HASH)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .failedLoginCount(2)
                .build();
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword@1", PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPassword@1")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_CREDENTIALS|Ten dang nhap hoac mat khau khong dung.");
                });

        assertThat(user.getFailedLoginCount()).isEqualTo(3);
        assertThat(user.getLastFailedLoginAt()).isNotNull();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
        verify(loginEventRecorder, never()).recordSuccessfulLogin(any());
    }

    @Test
    void loginWithWrongPasswordReachingMaxAttemptsLocksAccountForFifteenMinutes() {
        String email = "lockout-now@movehome.vn";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(PASSWORD_HASH)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .failedLoginCount(4)
                .build();
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword@1", PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "WrongPassword@1")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
                    assertThat(ex.getReason()).isEqualTo(
                            "ACCOUNT_LOCKED_NOW|Tai khoan bi khoa 15 phut do nhap sai mat khau qua nhieu lan.");
                });

        assertThat(user.getFailedLoginCount()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
        verify(userRepository).save(user);
    }

    // ===== LAM MOI TOKEN =====

    @Test
    void refreshWithUnknownTokenThrowsUnauthorized() {
        when(jwtTokenProvider.hashToken("unknown-raw")).thenReturn("unknown-hash");
        when(refreshTokenRepository.findByTokenHash("unknown-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("unknown-raw")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_REFRESH_TOKEN|Refresh token khong hop le. Vui long dang nhap lai.");
                });
    }

    @Test
    void refreshWithAlreadyRevokedTokenTriggersPanicModeAndRevokesAllSessions() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("revoked-hash")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revokedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();
        when(jwtTokenProvider.hashToken("stolen-raw")).thenReturn("revoked-hash");
        when(refreshTokenRepository.findByTokenHash("revoked-hash")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("stolen-raw")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo(
                            "TOKEN_REUSE_DETECTED|Phien lam viec bi nghi ngo. Tat ca phien da bi vo hieu. Vui long dang nhap lai.");
                });

        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void refreshWithExpiredTokenRevokesItAndThrowsUnauthorized() {
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("expired-hash")
                .expiresAt(Instant.now().minus(1, ChronoUnit.SECONDS))
                .build();
        when(jwtTokenProvider.hashToken("expired-raw")).thenReturn("expired-hash");
        when(refreshTokenRepository.findByTokenHash("expired-hash")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("expired-raw")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo(
                            "TOKEN_EXPIRED|Phien lam viec da het han. Vui long dang nhap lai.");
                });

        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void refreshWithMissingUserThrowsUnauthorized() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("orphan-hash")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        when(jwtTokenProvider.hashToken("orphan-raw")).thenReturn("orphan-hash");
        when(refreshTokenRepository.findByTokenHash("orphan-hash")).thenReturn(Optional.of(stored));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("orphan-raw")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo("INVALID_REFRESH_TOKEN|Tai khoan khong ton tai.");
                });
    }

    @Test
    void refreshForLockedUserThrowsLocked() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("locked-hash")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        User user = User.builder().id(userId).status(UserStatus.LOCKED).build();
        when(jwtTokenProvider.hashToken("locked-raw")).thenReturn("locked-hash");
        when(refreshTokenRepository.findByTokenHash("locked-hash")).thenReturn(Optional.of(stored));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("locked-raw")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
                    assertThat(ex.getReason()).isEqualTo(
                            "ACCOUNT_LOCKED|Tai khoan da bi khoa. Vui long lien he quan tri vien.");
                });
    }

    @Test
    void refreshForSuspendedUserThrowsForbidden() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("suspended-hash")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        User user = User.builder().id(userId).status(UserStatus.SUSPENDED).build();
        when(jwtTokenProvider.hashToken("suspended-raw")).thenReturn("suspended-hash");
        when(refreshTokenRepository.findByTokenHash("suspended-hash")).thenReturn(Optional.of(stored));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("suspended-raw")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).isEqualTo(
                            "ACCOUNT_SUSPENDED|Tai khoan da bi dinh chi. Vui long lien he quan tri vien.");
                });
    }

    @Test
    void refreshRotatesTokenAndReturnsNewAuthResponse() {
        UUID userId = UUID.randomUUID();
        UUID newTokenId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("current-hash")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        User user = User.builder()
                .id(userId)
                .email("active-driver@movehome.vn")
                .role(UserRole.DRIVER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
        when(jwtTokenProvider.hashToken("current-raw")).thenReturn("current-hash");
        when(refreshTokenRepository.findByTokenHash("current-hash")).thenReturn(Optional.of(stored));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-raw-refresh");
        when(jwtTokenProvider.hashToken("new-raw-refresh")).thenReturn("new-hash");
        when(jwtTokenProvider.refreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("new-access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(newTokenId);
            }
            return token;
        });

        AuthResponse response = authService.refresh(new RefreshRequest("current-raw"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-raw-refresh");
        assertThat(response.user().email()).isEqualTo("active-driver@movehome.vn");
        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(stored.getReplacedByTokenId()).isEqualTo(newTokenId);
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    private void stubSuccessfulLogin(User user) {
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.hashToken("refresh-token")).thenReturn("refresh-token-hash");
        when(jwtTokenProvider.refreshTokenExpiry())
                .thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));
    }

    private User verifiedUser(String email, UserRole role, UserStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(PASSWORD_HASH)
                .fullName("Người dùng kiểm thử")
                .role(role)
                .status(status)
                .emailVerified(true)
                .build();
    }

    private static Stream<Arguments> verifiedLoginCases() {
        return Stream.of(
                Arguments.of("driver_pending@movehome.vn", UserRole.DRIVER, UserStatus.PENDING_APPROVAL),
                Arguments.of("driver1@movehome.vn", UserRole.DRIVER, UserStatus.ACTIVE),
                Arguments.of("customer1@test.com", UserRole.CUSTOMER, UserStatus.ACTIVE),
                Arguments.of("admin@movehome.vn", UserRole.ADMIN, UserStatus.ACTIVE)
        );
    }
}
