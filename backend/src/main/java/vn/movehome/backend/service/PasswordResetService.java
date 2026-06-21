package vn.movehome.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {

    static final String NEUTRAL_RESPONSE =
            "Nếu email tồn tại trong hệ thống, chúng tôi sẽ gửi hướng dẫn đặt lại mật khẩu.";
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_EXPIRY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom;
    private final String resetPasswordUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${app.frontend.reset-password-url:http://localhost:5500/frontend/pages/reset-password.html}")
            String resetPasswordUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.resetPasswordUrl = resetPasswordUrl;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Luon tra cung mot noi dung, ke ca email khong ton tai, de chong user enumeration.
     */
    @Transactional
    public String requestReset(ForgotPasswordRequest request) {
        String normalizedEmail = request.email().strip().toLowerCase(Locale.ROOT);
        Optional<User> user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail);
        if (user.isEmpty()) {
            return NEUTRAL_RESPONSE;
        }

        Instant now = Instant.now();
        String rawToken = generateToken();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.get().getId())
                .tokenHash(hashToken(rawToken))
                .expiresAt(now.plus(TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .build();

        tokenRepository.markUnusedTokensAsUsed(user.get().getId(), now);
        tokenRepository.save(resetToken);
        sendResetEmailAfterCommit(user.get().getEmail(), rawToken);
        return NEUTRAL_RESPONSE;
    }

    /**
     * Doi mat khau va danh dau token da dung trong cung mot DB transaction.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = hashToken(request.token());
        PasswordResetToken resetToken = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> invalidToken(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                        "Liên kết đặt lại mật khẩu không hợp lệ."));

        Instant now = Instant.now();
        if (resetToken.getUsedAt() != null) {
            throw invalidToken(HttpStatus.CONFLICT, "TOKEN_ALREADY_USED",
                    "Liên kết đặt lại mật khẩu đã được sử dụng.");
        }
        if (!resetToken.getExpiresAt().isAfter(now)) {
            throw invalidToken(HttpStatus.GONE, "TOKEN_EXPIRED",
                    "Liên kết đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu liên kết mới.");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> invalidToken(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                        "Liên kết đặt lại mật khẩu không hợp lệ."));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetToken.setUsedAt(now);
        tokenRepository.save(resetToken);
    }

    private void sendResetEmailAfterCommit(String email, String rawToken) {
        String separator = resetPasswordUrl.contains("?") ? "&" : "?";
        String resetLink = resetPasswordUrl + separator + "token=" + rawToken;
        String htmlBody = """
                <p>Bạn đã yêu cầu đặt lại mật khẩu Move_home.</p>
                <p><a href="%s">Đặt lại mật khẩu</a></p>
                <p>Liên kết có hiệu lực trong 30 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
                """.formatted(HtmlUtils.htmlEscape(resetLink));
        Runnable sendEmail = () -> emailService.send(
                email,
                "Đặt lại mật khẩu Move_home",
                htmlBody);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEmail.run();
                }
            });
        } else {
            sendEmail.run();
        }
    }

    private String generateToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private ResponseStatusException invalidToken(HttpStatus status, String code, String message) {
        return new ResponseStatusException(status, code + "|" + message);
    }
}
