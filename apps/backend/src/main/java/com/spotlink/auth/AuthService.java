package com.spotlink.auth;

import com.spotlink.core.ConflictException;
import com.spotlink.core.NotFoundException;
import com.spotlink.operator.OperatorAccount;
import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.user.RegistrationStatus;
import com.spotlink.user.User;
import com.spotlink.user.UserPreferences;
import com.spotlink.user.UserPreferencesRepository;
import com.spotlink.user.UserRepository;
import com.spotlink.user.UserRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final UserPreferencesRepository preferences;
    private final OperatorAccountRepository operators;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthService(
            UserRepository users,
            UserPreferencesRepository preferences,
            OperatorAccountRepository operators,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.users = users;
        this.preferences = preferences;
        this.operators = operators;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public User registerCustomer(AuthDtos.RegisterCustomerRequest request) {
        ensureEmailAvailable(request.email());
        User user = baseUser(request.firstName(), request.lastName(), request.email(), request.phone(), request.password());
        user.setRoles(Set.of(UserRole.CUSTOMER));
        User saved = users.save(user);
        createPreferences(saved);
        return saved;
    }

    @Transactional
    public User registerOperator(AuthDtos.RegisterOperatorRequest request) {
        ensureEmailAvailable(request.email());
        User user = baseUser(request.firstName(), request.lastName(), request.email(), request.phone(), request.password());
        user.setRoles(Set.of(UserRole.CUSTOMER, UserRole.OPERATOR));
        User saved = users.save(user);
        createPreferences(saved);

        OperatorAccount operator = new OperatorAccount();
        operator.setUserId(saved.getId());
        operator.setDisplayName(Optional.ofNullable(request.companyName())
                .filter(value -> !value.isBlank())
                .orElse(saved.getFirstName() + " " + saved.getLastName()));
        operator.setLegalName(request.companyName());
        operator.setSupportEmail(saved.getEmail());
        operators.save(operator);
        return saved;
    }

    @Transactional
    public void requestPasswordReset(AuthDtos.PasswordResetRequest request) {
        users.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            String token = "sl_reset_" + UUID.randomUUID();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUserId(user.getId());
            resetToken.setTokenHash(hashToken(token));
            resetToken.setExpiresAt(Instant.now(clock).plus(30, ChronoUnit.MINUTES));
            resetTokens.save(resetToken);
            log.info("Password reset token generated for userId={} tokenPrefix={}", user.getId(), token.substring(0, 12));
        });
    }

    @Transactional
    public void completePasswordReset(AuthDtos.CompletePasswordResetRequest request) {
        PasswordResetToken token = resetTokens.findByTokenHashAndConsumedAtIsNull(hashToken(request.token()))
                .orElseThrow(() -> new NotFoundException("Password reset token was not found or has already been used."));
        if (Instant.now(clock).isAfter(token.getExpiresAt())) {
            throw new ConflictException("PASSWORD_RESET_EXPIRED", "Password reset token has expired.");
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found."));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        token.setConsumedAt(Instant.now(clock));
    }

    private User baseUser(String firstName, String lastName, String email, String phone, String password) {
        User user = new User();
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRegistrationStatus(RegistrationStatus.ACTIVE);
        return user;
    }

    private void ensureEmailAvailable(String email) {
        if (users.existsByEmailIgnoreCase(email.trim())) {
            throw new ConflictException("DUPLICATE_REGISTRATION", "An account with this email already exists.");
        }
    }

    private void createPreferences(User user) {
        UserPreferences prefs = new UserPreferences();
        prefs.setUserId(user.getId());
        preferences.save(prefs);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
