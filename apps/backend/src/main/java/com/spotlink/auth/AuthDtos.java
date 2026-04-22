package com.spotlink.auth;

import com.spotlink.user.UserDtos;
import com.spotlink.user.UserRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record RegisterCustomerRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email String email,
            @Size(max = 50) String phone,
            @NotBlank @Size(min = 8, max = 128) String password,
            @AssertTrue boolean acceptsTerms
    ) {
    }

    public record RegisterOperatorRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email String email,
            @Size(max = 50) String phone,
            @NotBlank @Size(min = 8, max = 128) String password,
            @AssertTrue boolean acceptsTerms,
            @Size(max = 160) String companyName,
            @NotNull OperatorType operatorType,
            @AssertTrue boolean acceptsOperatorAgreement
    ) {
    }

    public enum OperatorType {
        INDIVIDUAL,
        BUSINESS
    }

    public record AuthResponse(
            boolean authenticated,
            UserDtos.UserProfile user,
            String message
    ) {
    }

    public record PasswordResetRequest(@NotBlank @Email String email) {
    }

    public record CompletePasswordResetRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {
    }

    /** Zahtev za JWT token – koristiti iskljucivo za mobilne native klijente. */
    public record MobileTokenRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @Size(max = 160) String deviceId
    ) {
    }

    /** Odgovor sa JWT access tokenom za mobilni klijent. */
    public record MobileTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long expiresInSeconds,
            long refreshExpiresInSeconds,
            Instant issuedAt,
            Instant expiresAt,
            Instant refreshExpiresAt,
            UserDtos.UserProfile user,
            List<UserRole> roles
    ) {
    }

    public record RefreshTokenRequest(
            @NotBlank String refreshToken,
            @Size(max = 160) String deviceId
    ) {
    }

    public record RevokeTokenRequest(
            String refreshToken,
            Boolean allForCurrentUser
    ) {
    }
}
