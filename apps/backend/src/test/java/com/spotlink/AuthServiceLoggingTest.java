package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthServiceLoggingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void passwordResetRequestDoesNotLogTokenMaterial(CapturedOutput output) throws Exception {
        String email = "reset-%s@spotlink.test".formatted(UUID.randomUUID());

        mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Reset",
                                  "lastName": "Tester",
                                  "email": "%s",
                                  "phone": "+381600000003",
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

        assertThat(output).contains("Password reset delivery queued for userId=");
        assertThat(output).contains("Mail delivery captured provider=safe-log recipientHash=");
        assertThat(output).doesNotContain("tokenPrefix");
        assertThat(output).doesNotContain("sl_reset_");
        assertThat(output).doesNotContain("reset-password");
        assertThat(output).doesNotContain("SpotLink password reset");
        assertThat(output).doesNotContain(email);
    }

    @Test
    void missingPasswordResetAccountDoesNotLeakEnumerationSignals(CapturedOutput output) throws Exception {
        String email = "missing-%s@spotlink.test".formatted(UUID.randomUUID());

        mockMvc.perform(post("/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s"
                                }
                                """.formatted(email)))
                .andExpect(status().isNoContent());

        assertThat(output).doesNotContain("Mail delivery captured");
        assertThat(output).doesNotContain("Password reset delivery queued");
        assertThat(output).doesNotContain(email);
    }
}
