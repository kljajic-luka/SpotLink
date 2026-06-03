package com.spotlink.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.core.ApiErrorResponse;
import com.spotlink.core.AppProperties;
import com.spotlink.core.RequestCorrelationFilter;
import com.spotlink.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public SecurityConfig(AppProperties appProperties, ObjectMapper objectMapper,
            JwtService jwtService, UserRepository userRepository) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, userRepository);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .ignoringRequestMatchers(bearerRequestMatcher())
                        .ignoringRequestMatchers(
                                "/auth/login",
                                "/auth/logout",
                                "/auth/register/**",
                                "/auth/password/**",
                                "/auth/token",
                                "/auth/token/**",
                                "/v1/auth/login",
                                "/v1/auth/logout",
                                "/v1/auth/register/**",
                                "/v1/auth/password/**",
                                "/v1/auth/token",
                                "/v1/auth/token/**",
                                "/v1/analytics/events",
                                "/analytics/events"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/v1/health", "/actuator/health", "/actuator/health/**", "/actuator/info", "/openapi/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/auth/login", "/auth/logout", "/auth/register/**", "/auth/password/**", "/auth/token", "/auth/token/**").permitAll()
                        .requestMatchers("/v1/auth/login", "/v1/auth/logout", "/v1/auth/register/**", "/v1/auth/password/**", "/v1/auth/token", "/v1/auth/token/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/analytics/events").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/analytics/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/locations/**", "/v1/locations/**").permitAll()
                        .requestMatchers("/admin/**", "/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/operator/**", "/v1/operator/**").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> writeError(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Your session has expired. Sign in again to continue."))
                        .accessDeniedHandler((request, response, ex) -> writeError(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "FORBIDDEN",
                                "You do not have permission to perform this action.")))
                .securityContext(Customizer.withDefaults())
                .logout(logout -> logout.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'self'; base-uri 'self'; form-action 'self'"))
                        .referrerPolicy(Customizer.withDefaults()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private RequestMatcher bearerRequestMatcher() {
        return request -> {
            String authorization = request.getHeader("Authorization");
            return authorization != null && authorization.startsWith("Bearer ");
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appProperties.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-XSRF-TOKEN",
                "X-CSRF-TOKEN",
                "X-Idempotency-Key",
                "X-Request-Id",
                "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-Idempotency-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new CookieCsrfTokenRepository(appProperties);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message) throws IOException {
        String requestId = (String) request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(),
                code,
                message,
                requestId,
                Map.of(),
                request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
