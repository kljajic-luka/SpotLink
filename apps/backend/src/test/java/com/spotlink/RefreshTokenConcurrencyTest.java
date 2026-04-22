package com.spotlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifikuje da istovremeni rotate pozivi sa istim refresh tokenom
 * ne mogu oba da uspeju (race condition / double-spend napad).
 *
 * Ocekivano ponasanje:
 *  - Tacno jedan poziv dobija 200 i novi refresh token.
 *  - Svi ostali dobijaju 401 (token je vec opozvan od prvog poziva ili DB lock).
 *  - Nijedan korisnik ne dobija dva vazeca refresh tokena od jednog ulaznog.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentRotateWithSameTokenAllowsExactlyOneSuccess() throws Exception {
        String email = registerCustomer();
        String refreshToken = obtainRefreshToken(email);

        int threads = 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger rejections = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        String body = """
                {
                  "refreshToken": "%s",
                  "deviceId": "concurrent-test-device"
                }
                """.formatted(refreshToken);

        @SuppressWarnings("unchecked")
        Future<Void>[] futures = new Future[threads];
        for (int i = 0; i < threads; i++) {
            futures[i] = pool.submit(() -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);

                MvcResult result = mockMvc.perform(post("/auth/token/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();

                int status = result.getResponse().getStatus();
                if (status == 200) {
                    successes.incrementAndGet();
                } else if (status == 401) {
                    rejections.incrementAndGet();
                }
                return null;
            });
        }

        // Cekamo da svi niti budu spremne, pa ih pustamo odjednom
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        // Propaguj eventualne izuzetke iz niti
        for (Future<Void> f : futures) {
            f.get();
        }

        // Tacno jedan zahtev sme biti uspesno obradjeno
        assertThat(successes.get())
                .as("Tacno jedan od %d istovremenih rotate poziva mora uspeti", threads)
                .isEqualTo(1);

        // Svi ostali moraju biti odbijeni
        assertThat(rejections.get())
                .as("Svi ostali rotate pozivi moraju biti odbijeni (401)")
                .isEqualTo(threads - 1);
    }

    private String obtainRefreshToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "CorrectHorse123",
                                  "deviceId": "concurrency-test"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("refreshToken").asText();
    }

    private String registerCustomer() throws Exception {
        String email = "conc-%s@spotlink.test".formatted(UUID.randomUUID());
        mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Concurrent",
                                  "lastName": "Tester",
                                  "email": "%s",
                                  "phone": "+381600000099",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }
}
