package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.auth.LoginRequest;
import vn.movehome.backend.email.notification.EmailService;
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
import static org.mockito.Mockito.when;

/**
 * Kiem tra cac truong hop dang nhap that bai va khoa tai khoan.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginFailureTest {

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
                userRepository, emailTokenRepository,
                refreshTokenRepository, jwtTokenProvider, passwordEncoder,
                loginEventRecorder, emailService, driverProfileRepository,
                "http://localhost:5500/frontend/pages/verify-email-success.html");
    }

    @Test
    void loginFailsWhenUserNotFound() {
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@test.vn"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@test.vn", "AnyPass@1")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginFailsWhenPasswordIsWrong() {
        User user = verifiedUser();
        user.setPasswordHash("hash");
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass@1", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "WrongPass@1")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginFailsWhenAccountIsLocked() {
        User user = verifiedUser();
        user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES)); // Tai khoan dang bi khoa
        user.setPasswordHash("hash");
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "AnyPass@1")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.LOCKED));
    }

    @Test
    void loginFailsWhenEmailNotVerified() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("unverified@test.vn")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.PENDING_VERIFY)
                .emailVerified(false)
                .failedLoginCount(0)
                .build();
        when(userRepository.findByEmailAndDeletedAtIsNull("unverified@test.vn"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass@123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("unverified@test.vn", "Pass@123")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).contains("EMAIL_NOT_VERIFIED");
                });
    }

    @Test
    void loginIncrementsFailedCountOnWrongPassword() {
        User user = verifiedUser();
        user.setPasswordHash("hash");
        user.setFailedLoginCount(0);
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong@123", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "Wrong@123")))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(user.getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    void loginLocksAccountAfter5WrongPasswords() {
        User user = verifiedUser();
        user.setPasswordHash("hash");
        user.setFailedLoginCount(4); // 4 lan sai truoc do
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong@123", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "Wrong@123")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
                    assertThat(ex.getReason()).contains("ACCOUNT_LOCKED_NOW");
                });

        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
    }

    private User verifiedUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("customer@test.vn")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .failedLoginCount(0)
                .build();
    }
}
