package com.spotlink.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotlink.core.ApiErrorResponse;
import com.spotlink.core.AppProperties;
import com.spotlink.core.Constants;
import com.spotlink.core.OperationalMetrics;
import com.spotlink.core.RequestCorrelationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMITED_CODE = "RATE_LIMITED";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final AppProperties appProperties;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final OperationalMetrics metrics;
    private final List<EndpointLimit> endpointLimits;

    public RateLimitFilter(
            AppProperties appProperties,
            RateLimitService rateLimitService,
            ObjectMapper objectMapper,
            OperationalMetrics metrics) {
        this.appProperties = appProperties;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.endpointLimits = List.of(
                new EndpointLimit("auth_login", "/auth/login", appProperties.getRateLimit().getLogin()),
                new EndpointLimit("auth_login", "/v1/auth/login", appProperties.getRateLimit().getLogin()),
                new EndpointLimit("mobile_token", "/auth/token", appProperties.getRateLimit().getMobileToken()),
                new EndpointLimit("mobile_token", "/v1/auth/token", appProperties.getRateLimit().getMobileToken()),
                new EndpointLimit("registration", "/auth/register/customer", appProperties.getRateLimit().getRegistration()),
                new EndpointLimit("registration", "/v1/auth/register/customer", appProperties.getRateLimit().getRegistration()),
                new EndpointLimit("registration", "/auth/register/operator", appProperties.getRateLimit().getRegistration()),
                new EndpointLimit("registration", "/v1/auth/register/operator", appProperties.getRateLimit().getRegistration()),
                new EndpointLimit("password_reset_request", "/auth/password/reset-request", appProperties.getRateLimit().getPasswordResetRequest()),
                new EndpointLimit("password_reset_request", "/v1/auth/password/reset-request", appProperties.getRateLimit().getPasswordResetRequest()),
                new EndpointLimit("password_reset_complete", "/auth/password/reset", appProperties.getRateLimit().getPasswordResetComplete()),
                new EndpointLimit("password_reset_complete", "/v1/auth/password/reset", appProperties.getRateLimit().getPasswordResetComplete()),
                new EndpointLimit("analytics_ingest", "/analytics/events", appProperties.getRateLimit().getAnalytics()),
                new EndpointLimit("analytics_ingest", "/v1/analytics/events", appProperties.getRateLimit().getAnalytics()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!appProperties.getRateLimit().isEnabled() || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        EndpointLimit endpointLimit = endpointLimit(request);
        if (endpointLimit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = rateLimitService.consume(
                endpointLimit.name(),
                clientAddress(request),
                endpointLimit.rule());
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        metrics.increment("spotlink.security.rate_limit.blocked", "operation", endpointLimit.name());
        writeRateLimited(request, response, endpointLimit, decision);
    }

    private EndpointLimit endpointLimit(HttpServletRequest request) {
        String path = normalizedPath(request);
        for (EndpointLimit endpointLimit : endpointLimits) {
            if (endpointLimit.path().equals(path)) {
                return endpointLimit;
            }
        }
        return null;
    }

    private String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private String clientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return StringUtils.hasText(request.getRemoteAddr()) ? request.getRemoteAddr() : "unknown";
    }

    private void writeRateLimited(
            HttpServletRequest request,
            HttpServletResponse response,
            EndpointLimit endpointLimit,
            RateLimitDecision decision) throws IOException {
        String requestId = requestId(request);
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                RATE_LIMITED_CODE,
                "Too many requests. Wait before retrying.",
                requestId,
                Map.of(
                        "operation", endpointLimit.name(),
                        "limit", decision.limit()),
                request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(Constants.REQUEST_ID_HEADER, requestId);
        response.setHeader(RETRY_AFTER_HEADER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        if (attribute instanceof String requestId && StringUtils.hasText(requestId)) {
            return requestId;
        }
        String header = request.getHeader(Constants.REQUEST_ID_HEADER);
        return StringUtils.hasText(header) ? header : "sl-rate-limited";
    }

    private record EndpointLimit(
            String name,
            String path,
            AppProperties.RateLimit.Rule rule
    ) {
    }
}
