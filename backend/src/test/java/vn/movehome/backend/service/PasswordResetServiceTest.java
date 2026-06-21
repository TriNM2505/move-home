package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.auth.ForgotPasswordRequest;
import vn.movehome.backend.dto.auth.ResetPasswordRequest;
import vn.movehome.backend.email.notification.EmailService;
import vn.movehome.backend.entity.PasswordResetToken;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.repository.PasswordResetTokenRepository;
import vn.movehome.backend.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String EMAIL = "customer@movehome.vn";
    private static final String RESET_URL = "http://localhost:5500/frontend/pages/reset-password.html";
    private static final String NEW_PASSWORD = "NewPassword@2026";
    private static final Pattern TOKEN_IN_EMAIL = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    private PasswordEncoder passwordEncoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        service = new PasswordResetService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                emailService,
                RESET_URL);
    }

    @Test
    void forgotPasswordStoresOnlyHashAndSendsRawTokenInFrontendLink() {
        User user = User.builder().id(UUID.randomUUID()).email(EMAIL).build();
        when(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Instant before = Instant.now();

        String response = service.requestReset(new ForgotPasswordRequest("  CUSTOMER@MOVEHOME.VN "));

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken storedToken = tokenCaptor.getValue();

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq(EMAIL), eq("Đặt lại mật khẩu Move_home"), htmlCaptor.capture());
        String rawToken = extractToken(htmlCaptor.getValue());

        assertThat(response).isEqualTo(PasswordResetService.NEUTRAL_RESPONSE);
        assertThat(rawToken).hasSize(43);
        assertThat(storedToken.getTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(storedToken.getTokenHash()).doesNotContain(rawToken);
        assertThat(storedToken.getExpiresAt())
                .isAfterOrEqualTo(before.plus(30, ChronoUnit.MINUTES))
                .isBeforeOrEqualTo(Instant.now().plus(30, ChronoUnit.MINUTES));
        verify(tokenRepository).markUnusedTokensAsUsed(eq(user.getId()), any(Instant.class));
    }

    @Test
    void forgotPasswordReturnsSameResponseForUnknownEmailWithoutSendingMail() {
        when(userRepository.findByEmailAndDeletedAtIsNull("unknown@movehome.vn"))
                .thenReturn(Optional.empty());

        String response = service.requestReset(new ForgotPasswordRequest("unknown@movehome.vn"));

        assertThat(response).isEqualTo(PasswordResetService.NEUTRAL_RESPONSE);
        verifyNoInteractions(tokenRepository, emailService);
    }

    @Test
    void validTokenChangesBcryptPasswordAndMarksTokenUsed() {
        String rawToken = "valid-reset-token";
        UUID userId = UUID.randomUUID();
        PasswordResetToken resetToken = token(userId, Instant.now().plus(5, ChronoUnit.MINUTES), null);
        User user = User.builder()
                .id(userId)
                .passwordHash(passwordEncoder.encode("OldPassword@2026"))
                .failedLoginCount(4)
                .lockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        when(tokenRepository.findByTokenHashForUpdate(sha256(rawToken))).thenReturn(Optional.of(resetToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.resetPassword(new ResetPasswordRequest(rawToken, NEW_PASSWORD));

        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).startsWith("$2a$12$");
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(resetToken.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(tokenRepository).save(resetToken);
    }

    @Test
    void expiredTokenIsRejectedWithoutChangingPassword() {
        String rawToken = "expired-reset-token";
        PasswordResetToken resetToken = token(
                UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.SECONDS),
                null);
        when(tokenRepository.findByTokenHashForUpdate(sha256(rawToken))).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(rawToken, NEW_PASSWORD)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.GONE);
                    assertThat(exception.getReason()).startsWith("TOKEN_EXPIRED|");
                });

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void usedTokenIsRejectedWithoutChangingPassword() {
        String rawToken = "used-reset-token";
        PasswordResetToken resetToken = token(
                UUID.randomUUID(),
                Instant.now().plus(5, ChronoUnit.MINUTES),
                Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepository.findByTokenHashForUpdate(sha256(rawToken))).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(rawToken, NEW_PASSWORD)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).startsWith("TOKEN_ALREADY_USED|");
                });

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    private PasswordResetToken token(UUID userId, Instant expiresAt, Instant usedAt) {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("stored-hash")
                .expiresAt(expiresAt)
                .usedAt(usedAt)
                .build();
    }

    private String extractToken(String html) {
        Matcher matcher = TOKEN_IN_EMAIL.matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
