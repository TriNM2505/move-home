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
import vn.movehome.backend.dto.auth.RegisterCustomerRequest;
import vn.movehome.backend.dto.auth.VerifyEmailRequest;
import vn.movehome.backend.email.notification.EmailService;
import vn.movehome.backend.entity.EmailVerificationToken;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiem tra luong dang ky Customer va xac thuc email.
 * AuthServiceTest.java kiem tra luong dang nhap; file nay kiem tra register va verifyEmail.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterAndVerifyTest {

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
                "http://localhost:5500/frontend/pages/verify-email-success.html");
    }

    // ===== DANG KY =====

    @Test
    void registerCustomerSavesUserWithCorrectRoleAndPendingStatus() {
        RegisterCustomerRequest req = new RegisterCustomerRequest(
                "new@test.vn", "StrongPass@123", "Nguyễn Văn A", "0912345678", true);
        when(userRepository.existsByEmail("new@test.vn")).thenReturn(false);

        UUID savedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u = User.builder()
                    .id(savedId).email(u.getEmail()).passwordHash(u.getPasswordHash())
                    .fullName(u.getFullName()).phone(u.getPhone())
                    .role(UserRole.CUSTOMER).status(UserStatus.PENDING_VERIFY)
                    .emailVerified(false)
                    .build();
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-raw");
        when(jwtTokenProvider.hashToken(any())).thenReturn("some-hash");
        when(jwtTokenProvider.refreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        authService.registerCustomer(req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFY);
        assertThat(saved.isEmailVerified()).isFalse();
    }

    @Test
    void registerCustomerRejectsAlreadyUsedEmail() {
        when(userRepository.existsByEmail("taken@test.vn")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerCustomer(
                new RegisterCustomerRequest("taken@test.vn", "Pass@123456", "Some User", "0912345678", true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerCustomerNormalizesPhoneNumberFrom0To84Prefix() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        UUID savedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder().id(savedId).email(u.getEmail())
                    .passwordHash("hash").fullName(u.getFullName()).phone(u.getPhone())
                    .role(UserRole.CUSTOMER).status(UserStatus.PENDING_VERIFY)
                    .emailVerified(false).build();
        });
        when(jwtTokenProvider.hashToken(any())).thenReturn("hash");
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("at");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("rt");
        when(jwtTokenProvider.refreshTokenExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        authService.registerCustomer(
                new RegisterCustomerRequest("phone@test.vn", "Pass@123456", "Phone User", "0987654321", true));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getPhone()).isEqualTo("+84987654321");
    }

    // ===== XAC THUC EMAIL =====

    @Test
    void verifyEmailActivatesCustomerToActive() {
        UUID userId = UUID.randomUUID();
        String rawToken = "raw-token";
        String tokenHash = "sha256-hash";
        User user = User.builder()
                .id(userId).email("customer@test.vn")
                .role(UserRole.CUSTOMER).status(UserStatus.PENDING_VERIFY)
                .emailVerified(false)
                .build();
        EmailVerificationToken evToken = EmailVerificationToken.builder()
                .userId(userId).token(tokenHash)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        when(jwtTokenProvider.hashToken(rawToken)).thenReturn(tokenHash);
        when(emailTokenRepository.findByToken(tokenHash)).thenReturn(Optional.of(evToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.verifyEmail(new VerifyEmailRequest(rawToken));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailSetsDriverStatusToPendingDocuments() {
        UUID userId = UUID.randomUUID();
        String rawToken = "raw-token";
        String tokenHash = "sha256-hash";
        User driver = User.builder()
                .id(userId).email("driver@test.vn")
                .role(UserRole.DRIVER).status(UserStatus.PENDING_VERIFY)
                .emailVerified(false)
                .build();
        EmailVerificationToken evToken = EmailVerificationToken.builder()
                .userId(userId).token(tokenHash)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        when(jwtTokenProvider.hashToken(rawToken)).thenReturn(tokenHash);
        when(emailTokenRepository.findByToken(tokenHash)).thenReturn(Optional.of(evToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(driver));

        authService.verifyEmail(new VerifyEmailRequest(rawToken));

        assertThat(driver.getStatus()).isEqualTo(UserStatus.PENDING_DOCUMENTS);
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        String rawToken = "expired-raw";
        String hash = "expired-hash";
        EmailVerificationToken expiredToken = EmailVerificationToken.builder()
                .userId(UUID.randomUUID()).token(hash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // Da het han
                .build();
        when(jwtTokenProvider.hashToken(rawToken)).thenReturn(hash);
        when(emailTokenRepository.findByToken(hash)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest(rawToken)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void verifyEmailRejectsAlreadyUsedToken() {
        String rawToken = "used-raw";
        String hash = "used-hash";
        EmailVerificationToken usedToken = EmailVerificationToken.builder()
                .userId(UUID.randomUUID()).token(hash)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .usedAt(Instant.now().minus(5, ChronoUnit.MINUTES)) // Da dung
                .build();
        when(jwtTokenProvider.hashToken(rawToken)).thenReturn(hash);
        when(emailTokenRepository.findByToken(hash)).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest(rawToken)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void verifyEmailRejectsUnknownToken() {
        String rawToken = "unknown-raw";
        when(jwtTokenProvider.hashToken(rawToken)).thenReturn("unknown-hash");
        when(emailTokenRepository.findByToken("unknown-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest(rawToken)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
