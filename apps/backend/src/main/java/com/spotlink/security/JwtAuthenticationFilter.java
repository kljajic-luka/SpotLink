package com.spotlink.security;

import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validira Authorization: Bearer <jwt> header i postavlja SecurityContext.
 * Izvrsava se pre UsernamePasswordAuthenticationFilter.
 * Ako ne postoji Bearer token, filter propusta request dalje
 * (cookie/session auth ce se izvrsiti normalno).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtService.parseAndValidate(token);
            UUID userId = UUID.fromString(claims.getSubject());

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null && user.getRegistrationStatus() == RegistrationStatus.ACTIVE) {
                    SpotLinkPrincipal principal = new SpotLinkPrincipal(user);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // Nevalidan token – nastaviti bez autentifikacije; 401 vraca security layer
        }

        filterChain.doFilter(request, response);
    }
}
