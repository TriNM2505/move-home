package vn.movehome.backend.email.notification;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * Gửi email HTML qua Gmail SMTP.
 * HR-01: thông tin SMTP chỉ lấy từ biến môi trường.
 * HR-11: gửi bất đồng bộ và mọi lỗi email không được làm rollback nghiệp vụ chính.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final int mailPort;
    private final String mailUsername;
    private final String mailPassword;

    public EmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.port:587}") int mailPort,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
    }

    /**
     * Gửi email HTML trên thread pool riêng. Phương thức luôn nuốt lỗi SMTP theo HR-11.
     */
    @Async(EmailAsyncConfig.EMAIL_EXECUTOR_BEAN)
    public void send(String to, String subject, String htmlBody) {
        if (!hasValidMessage(to, subject, htmlBody)) {
            log.warn("Bỏ qua email vì người nhận, tiêu đề hoặc nội dung không hợp lệ");
            return;
        }

        if (!isSmtpConfigured()) {
            log.warn("Chưa cấu hình đầy đủ MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD; bỏ qua email");
            return;
        }

        try {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                log.warn("JavaMailSender chưa sẵn sàng; bỏ qua email để không ảnh hưởng nghiệp vụ chính");
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(mailUsername);
            helper.setTo(to.strip());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.debug("Đã gửi email thành công");
        } catch (Exception exception) {
            // Không log địa chỉ email, nội dung hoặc credential để tránh lộ PII/secret.
            log.warn("Gửi email thất bại; nghiệp vụ chính vẫn tiếp tục. Loại lỗi: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private boolean isSmtpConfigured() {
        return StringUtils.hasText(mailHost)
                && mailPort > 0
                && StringUtils.hasText(mailUsername)
                && StringUtils.hasText(mailPassword);
    }

    private boolean hasValidMessage(String to, String subject, String htmlBody) {
        return StringUtils.hasText(to)
                && StringUtils.hasText(subject)
                && StringUtils.hasText(htmlBody);
    }
}
