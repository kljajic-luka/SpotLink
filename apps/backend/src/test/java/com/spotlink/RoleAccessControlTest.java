package com.spotlink;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.auth.AuthDtos;
import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import com.spotlink.user.UserRole;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifikacija role-based pristupa za admin i operator rute.
 *
 * Pokriva:
 *   - Neprijavljeni pristup → 401
 *   - CUSTOMER na admin rutama → 403
 *   - CUSTOMER na operator rutama → 403
 *   - OPERATOR na admin rutama → 403
 *   - ADMIN na admin rutama → 200
 *   - OPERATOR na operator rutama → 200
 *   - ADMIN na operator rutama → 403 (URL ruta dozvoljena, ali servis zahteva OperatorAccount koji ADMIN nema)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── admin rute ─────────────────────────────────────────────────────────────

    @Test
    void adminDashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void adminDashboardForbidsCustomer() throws Exception {
        MockHttpSession customerSession = registerCustomer("Mila");
        mockMvc.perform(get("/admin/dashboard/summary").session(customerSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminDashboardForbidsOperator() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        mockMvc.perform(get("/admin/dashboard/summary").session(operatorSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminDashboardAllowsAdmin() throws Exception {
        MockHttpSession adminSession = createAdminSession();
        mockMvc.perform(get("/admin/dashboard/summary").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeReservations").isNumber());
    }

    @Test
    void adminBookingsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminBookingsForbidsCustomer() throws Exception {
        MockHttpSession customerSession = registerCustomer("Nikola");
        mockMvc.perform(get("/admin/bookings").session(customerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminBookingsAllowsAdmin() throws Exception {
        MockHttpSession adminSession = createAdminSession();
        mockMvc.perform(get("/admin/bookings").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminCancelBookingRequiresAdminRole() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        mockMvc.perform(post("/admin/bookings/%s/cancel".formatted(UUID.randomUUID()))
                        .with(csrf())
                        .session(operatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAuditEventsAllowsAdmin() throws Exception {
        MockHttpSession adminSession = createAdminSession();
        mockMvc.perform(get("/admin/audit-events").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminAuditEventsForbidsCustomer() throws Exception {
        MockHttpSession customerSession = registerCustomer("Ana");
        mockMvc.perform(get("/admin/audit-events").session(customerSession))
                .andExpect(status().isForbidden());
    }

    // ── operator rute ──────────────────────────────────────────────────────────

    @Test
    void operatorMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/operator/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void operatorMeForbidsCustomer() throws Exception {
        MockHttpSession customerSession = registerCustomer("Jovana");
        mockMvc.perform(get("/operator/me").session(customerSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void operatorDashboardAllowsOperator() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        mockMvc.perform(get("/operator/dashboard/summary").session(operatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeLocations").isNumber());
    }

    @Test
    void operatorDashboardForbidsAdminWithoutOperatorAccount() throws Exception {
        // URL sigurnosni sloj dopusta ADMIN; servis baca AccessDeniedException jer ADMIN nema OperatorAccount
        MockHttpSession adminSession = createAdminSession();
        mockMvc.perform(get("/operator/dashboard/summary").session(adminSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void operatorBookingsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/operator/bookings/upcoming"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operatorBookingsForbidsCustomer() throws Exception {
        MockHttpSession customerSession = registerCustomer("Stefan");
        mockMvc.perform(get("/operator/bookings/upcoming").session(customerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorBookingsAllowsOperator() throws Exception {
        MockHttpSession operatorSession = registerOperator();
        mockMvc.perform(get("/operator/bookings/upcoming").session(operatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void v1AliasInheritsRoleGuard() throws Exception {
        // v1 alias podleze istim pravilima kao i obicna ruta
        mockMvc.perform(get("/v1/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized());

        MockHttpSession adminSession = createAdminSession();
        mockMvc.perform(get("/v1/admin/dashboard/summary").session(adminSession))
                .andExpect(status().isOk());
    }

    // ── pomocne metode ─────────────────────────────────────────────────────────

    private MockHttpSession registerCustomer(String firstName) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "%s",
                                  "lastName": "Testirani",
                                  "email": "customer-%s@spotlink.test",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true
                                }
                                """.formatted(firstName, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession registerOperator() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register/operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Operater",
                                  "lastName": "Testirani",
                                  "email": "operator-%s@spotlink.test",
                                  "password": "CorrectHorse123",
                                  "acceptsTerms": true,
                                  "companyName": "Test Parking",
                                  "operatorType": "BUSINESS",
                                  "acceptsOperatorAgreement": true
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession createAdminSession() throws Exception {
        String email = "admin-acl-%s@spotlink.test".formatted(UUID.randomUUID());
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode("CorrectHorse123"));
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setRegistrationStatus(RegistrationStatus.ACTIVE);
        admin.setRoles(Set.of(UserRole.ADMIN));
        userRepository.saveAndFlush(admin);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthDtos.LoginRequest(email, "CorrectHorse123"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
