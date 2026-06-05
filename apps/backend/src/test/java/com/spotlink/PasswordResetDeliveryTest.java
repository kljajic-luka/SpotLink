package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spotlink.auth.PasswordResetTokenRepository;
import com.spotlink.notification.MailProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spotlink.password-reset.reset-url=https://app.spotlink.test/reset-password")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PasswordResetDeliveryTest.MailTestConfig.class)
class PasswordResetDeliveryTest {

    private static final Pattern RESET_TOKEN = Pattern.compile("token=(sl_reset_[A-Za-z0-9-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingMailProvider mailProvider;

    @Autowired
    private PasswordResetTokenRepository resetTokens;

    @BeforeEach
    void setUp() {
        mailProvider.clear();
    }

    @Test
    void activeAccountReceivesResetMailAndCanCompleteReset() throws Exception {
        String email = "reset-%s@spotlink.test".formatted(UUID.randomUUID());

        mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Reset",
                                  "lastName": "Ready",
                                  "email": "%s",
                                  "phone": "+381600000011",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s"
                                }
                                """.formatted(email)))
                .andExpect(status().isNoContent());

        assertThat(mailProvider.messages()).hasSize(1);
        CapturedMail message = mailProvider.messages().getFirst();
        assertThat(message.to()).isEqualTo(email);
        assertThat(message.subject()).isEqualTo("SpotLink password reset");
        assertThat(message.body()).contains("https://app.spotlink.test/reset-password?token=sl_reset_");
        assertThat(resetTokens.findAll())
                .allSatisfy(token -> assertThat(token.getTokenHash()).doesNotContain("sl_reset_"));

        String resetToken = resetToken(message.body());
        mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "CorrectHorse456"
                                }
                                """.formatted(resetToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "CorrectHorse456"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void missingAccountDoesNotSendResetMail() throws Exception {
        mockMvc.perform(post("/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing-%s@spotlink.test"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNoContent());

        assertThat(mailProvider.messages()).isEmpty();
    }

    private String resetToken(String body) {
        Matcher matcher = RESET_TOKEN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    record CapturedMail(String to, String subject, String body) {
    }

    static final class CapturingMailProvider implements MailProvider {
        private final List<CapturedMail> messages = new ArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            messages.add(new CapturedMail(to, subject, body));
        }

        List<CapturedMail> messages() {
            return messages;
        }

        void clear() {
            messages.clear();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailTestConfig {
        @Bean
        @Primary
        CapturingMailProvider capturingMailProvider() {
            return new CapturingMailProvider();
        }
    }
}
