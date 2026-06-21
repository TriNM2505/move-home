package vn.movehome.backend.email.notification;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void missingSmtpConfigurationSkipsEmailWithoutThrowing() {
        EmailService emailService = new EmailService(mailSenderProvider, "", 587, "", "");

        assertThatCode(() -> emailService.send(
                "customer@example.com",
                "Xác thực tài khoản",
                "<strong>Xin chào</strong>"))
                .doesNotThrowAnyException();

        verifyNoInteractions(mailSenderProvider, mailSender);
    }

    @Test
    void configuredServiceSendsUtf8HtmlEmail() throws Exception {
        EmailService emailService = configuredEmailService();
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.send(
                "customer@example.com",
                "Xác thực tài khoản",
                "<strong>Xin chào Move_home</strong>");

        verify(mailSender).send(mimeMessage);
        // JavaMailSenderImpl gọi saveChanges() khi gửi; mock cần finalize MIME thủ công.
        mimeMessage.saveChanges();
        assertThat(mimeMessage.getSubject()).isEqualTo("Xác thực tài khoản");
        assertThat(mimeMessage.getContentType()).containsIgnoringCase("text/html");
        assertThat(mimeMessage.getAllRecipients()).hasSize(1);
    }

    @Test
    void smtpFailureDoesNotPropagateToCaller() {
        EmailService emailService = configuredEmailService();
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP tạm thời không khả dụng"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> emailService.send(
                "customer@example.com",
                "Thông báo đơn hàng",
                "<p>Đơn hàng đã được cập nhật.</p>"))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidMessageSkipsMailSender() {
        EmailService emailService = configuredEmailService();

        emailService.send(" ", "Tiêu đề", "<p>Nội dung</p>");

        verify(mailSenderProvider, never()).getIfAvailable();
    }

    private EmailService configuredEmailService() {
        return new EmailService(
                mailSenderProvider,
                "smtp.gmail.com",
                587,
                "no-reply@movehome.vn",
                "app-password");
    }
}
