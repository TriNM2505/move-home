package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.auth.AuthResponse;
import vn.movehome.backend.dto.auth.LoginRequest;
import vn.movehome.backend.email.notification.EmailService;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                            "ACCOUNT_SUSPENDED|Tai khoan da bi dinh chi. Vui long lien he quan tri vien.");
                });

        assertThat(user.isAccountNonLocked()).isFalse();
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any());
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
