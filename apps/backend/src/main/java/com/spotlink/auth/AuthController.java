package com.spotlink.auth;

import com.spotlink.operator.OperatorAccountRepository;
import com.spotlink.security.CurrentUserService;
import com.spotlink.security.JwtService;
import com.spotlink.security.SpotLinkPrincipal;
import com.spotlink.user.User;
import com.spotlink.user.UserMapper;
import com.spotlink.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final CurrentUserService currentUser;
    private final UserMapper userMapper;
    private final OperatorAccountRepository operators;
    private final CsrfTokenRepository csrfTokenRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            AuthenticationManager authenticationManager,
            AuthService authService,
            CurrentUserService currentUser,
            UserMapper userMapper,
            OperatorAccountRepository operators,
            CsrfTokenRepository csrfTokenRepository,
            JwtService jwtService,
            UserRepository userRepository,
            RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.authService = authService;
        this.currentUser = currentUser;
        this.userMapper = userMapper;
        this.operators = operators;
        this.csrfTokenRepository = csrfTokenRepository;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping({"/auth/login", "/v1/auth/login"})
    AuthDtos.AuthResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        establishSession(authentication, httpRequest, httpResponse);
        User user = currentUser.user();
        return new AuthDtos.AuthResponse(
                true,
                userMapper.toProfile(user, operators.findByUserId(user.getId())),
                "Authenticated");
    }

    @PostMapping({"/auth/register/customer", "/v1/auth/register/customer"})
    @ResponseStatus(HttpStatus.CREATED)
    AuthDtos.AuthResponse registerCustomer(
            @Valid @RequestBody AuthDtos.RegisterCustomerRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        User user = authService.registerCustomer(request);
        establishSession(new UsernamePasswordAuthenticationToken(
                new SpotLinkPrincipal(user),
                null,
                new SpotLinkPrincipal(user).getAuthorities()), httpRequest, httpResponse);
        return new AuthDtos.AuthResponse(
                true,
                userMapper.toProfile(user, operators.findByUserId(user.getId())),
                "Registered");
    }

    @PostMapping({"/auth/register/operator", "/v1/auth/register/operator"})
    @ResponseStatus(HttpStatus.CREATED)
    AuthDtos.AuthResponse registerOperator(
            @Valid @RequestBody AuthDtos.RegisterOperatorRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        User user = authService.registerOperator(request);
        SpotLinkPrincipal principal = new SpotLinkPrincipal(user);
        establishSession(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()), httpRequest, httpResponse);
        return new AuthDtos.AuthResponse(
                true,
                userMapper.toProfile(user, operators.findByUserId(user.getId())),
                "Registered");
    }

    @GetMapping({"/auth/me", "/v1/auth/me"})
    com.spotlink.user.UserDtos.UserProfile me() {
        User user = currentUser.user();
        return userMapper.toProfile(user, operators.findByUserId(user.getId()));
    }

    @PostMapping({"/auth/logout", "/v1/auth/logout"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(
            @RequestBody(required = false) AuthDtos.RevokeTokenRequest tokenRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        revokeMobileTokenIfRequested(tokenRequest);
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        csrfTokenRepository.saveToken(null, request, response);
    }

    @PostMapping({"/auth/password/reset-request", "/v1/auth/password/reset-request"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void requestPasswordReset(@Valid @RequestBody AuthDtos.PasswordResetRequest request) {
        authService.requestPasswordReset(request);
    }

    @PostMapping({"/auth/password/reset", "/v1/auth/password/reset"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void completePasswordReset(@Valid @RequestBody AuthDtos.CompletePasswordResetRequest request) {
        authService.completePasswordReset(request);
    }

    /**
     * Mobile-only: vraca JWT bearer token.
     * Web frontend koristi /auth/login koji postavlja cookie/session.
     */
    @PostMapping({"/auth/token", "/v1/auth/token"})
    AuthDtos.MobileTokenResponse mobileToken(
            @Valid @RequestBody AuthDtos.MobileTokenRequest request,
            HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        SpotLinkPrincipal principal = (SpotLinkPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found after auth"));
        return issueMobileSession(user, request.deviceId(), httpRequest);
    }

    @PostMapping({"/auth/token/refresh", "/v1/auth/token/refresh"})
    AuthDtos.MobileTokenResponse refreshToken(
            @Valid @RequestBody AuthDtos.RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(
                request.refreshToken(),
                firstNonBlank(request.deviceId(), httpRequest.getHeader("X-Device-Id")),
                httpRequest.getHeader("User-Agent"));
        return issueMobileSession(rotation.user(), rotation.refreshToken(), httpRequest);
    }

    @PostMapping({"/auth/token/revoke", "/v1/auth/token/revoke"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeToken(@RequestBody(required = false) AuthDtos.RevokeTokenRequest request) {
        if (request == null) {
            throw new BadCredentialsException("Refresh token is required.");
        }
        if (StringUtils.hasText(request.refreshToken())) {
            refreshTokenService.revoke(request.refreshToken());
            return;
        }
        if (Boolean.TRUE.equals(request.allForCurrentUser())) {
            refreshTokenService.revokeAllForUser(currentPrincipal()
                    .orElseThrow(() -> new AccessDeniedException("Authenticated user is required."))
                    .getUserId());
            return;
        }
        throw new BadCredentialsException("Refresh token is required.");
    }

    private void establishSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
    }

    private AuthDtos.MobileTokenResponse issueMobileSession(User user, String deviceId, HttpServletRequest request) {
        return issueMobileSession(
                user,
                refreshTokenService.issue(
                        user,
                        firstNonBlank(deviceId, request.getHeader("X-Device-Id")),
                        request.getHeader("User-Agent")),
                request);
    }

    private AuthDtos.MobileTokenResponse issueMobileSession(
            User user,
            RefreshTokenService.IssuedRefreshToken refreshToken,
            HttpServletRequest request) {
        JwtService.AccessToken accessToken = jwtService.issueAccessToken(user);
        List<com.spotlink.user.UserRole> roles = user.getRoles().stream().toList();
        long refreshExpiresInSeconds = Math.max(
                0,
                refreshToken.entity().getExpiresAt().getEpochSecond() - accessToken.issuedAt().getEpochSecond());
        return new AuthDtos.MobileTokenResponse(
                accessToken.token(),
                refreshToken.rawToken(),
                "Bearer",
                accessToken.expiresInSeconds(),
                accessToken.expiresInSeconds(),
                refreshExpiresInSeconds,
                accessToken.issuedAt(),
                accessToken.expiresAt(),
                refreshToken.entity().getExpiresAt(),
                userMapper.toProfile(user, operators.findByUserId(user.getId())),
                roles);
    }

    private void revokeMobileTokenIfRequested(AuthDtos.RevokeTokenRequest request) {
        if (request == null) {
            return;
        }
        if (StringUtils.hasText(request.refreshToken())) {
            refreshTokenService.revoke(request.refreshToken());
        }
        if (Boolean.TRUE.equals(request.allForCurrentUser())) {
            currentPrincipal().ifPresent(principal -> refreshTokenService.revokeAllForUser(principal.getUserId()));
        }
    }

    private Optional<SpotLinkPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SpotLinkPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
