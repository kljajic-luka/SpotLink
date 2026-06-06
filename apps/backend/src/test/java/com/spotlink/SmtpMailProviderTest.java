package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotlink.core.AppProperties;
import com.spotlink.notification.SmtpMailProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@ExtendWith(OutputCaptureExtension.class)
class SmtpMailProviderTest {

    @Test
    void sendsSimpleMessageWithoutLoggingSensitiveContent(CapturedOutput output) {
        AppProperties.Mail mail = completeMailProperties();
        CapturingMailSender sender = new CapturingMailSender();
        SmtpMailProvider provider = new SmtpMailProvider(mail, sender);

        String email = "reset-target@spotlink.test";
        String body = """
                Reset your SpotLink password using this link:

                https://app.spotlink.test/reset-password?token=sl_reset_sensitive-token
                """;
        provider.send(email, "SpotLink password reset", body);

        assertThat(sender.message).isNotNull();
        assertThat(sender.message.getFrom()).isEqualTo("no-reply@spotlink.app");
        assertThat(sender.message.getTo()).containsExactly(email);
        assertThat(sender.message.getSubject()).isEqualTo("SpotLink password reset");
        assertThat(sender.message.getText()).isEqualTo(body);

        assertThat(output).contains("Mail delivery queued provider=smtp recipientHash=");
        assertThat(output).doesNotContain(email);
        assertThat(output).doesNotContain("sl_reset_sensitive-token");
        assertThat(output).doesNotContain("Reset your SpotLink password");
        assertThat(output).doesNotContain("SpotLink password reset");
    }

    @Test
    void placeholderSecretsAreNotProductionReady() {
        AppProperties.Mail mail = completeMailProperties();
        mail.getSmtp().setPassword("<smtp-password-from-secret-store>");

        assertThat(new SmtpMailProvider(mail, new CapturingMailSender()).productionReady()).isFalse();
    }

    private AppProperties.Mail completeMailProperties() {
        AppProperties.Mail mail = new AppProperties.Mail();
        mail.setProvider("smtp");
        mail.getSmtp().setHost("smtp.example.net");
        mail.getSmtp().setPort(587);
        mail.getSmtp().setFrom("no-reply@spotlink.app");
        mail.getSmtp().setStarttlsEnabled(true);
        mail.getSmtp().setAuthEnabled(true);
        mail.getSmtp().setUsername("spotlink-sender");
        mail.getSmtp().setPassword("smtp-password-for-tests");
        return mail;
    }

    private static final class CapturingMailSender extends JavaMailSenderImpl {
        private SimpleMailMessage message;

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            this.message = new SimpleMailMessage(simpleMessage);
        }
    }
}
