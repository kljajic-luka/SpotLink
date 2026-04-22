package com.spotlink.security;

import com.spotlink.core.AppProperties;
import com.spotlink.core.Constants;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

public class CookieCsrfTokenRepository implements CsrfTokenRepository {

    private final SecureRandom secureRandom = new SecureRandom();
    private final AppProperties appProperties;

    public CookieCsrfTokenRepository(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return new DefaultCsrfToken(
                Constants.XSRF_HEADER,
                "_csrf",
                Base64.getUrlEncoder().withoutPadding().encodeToString(token));
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        if (token == null || !StringUtils.hasText(token.getToken())) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
            return;
        }
        Cookie existing = WebUtils.getCookie(request, Constants.XSRF_COOKIE);
        if (existing != null && token.getToken().equals(existing.getValue())) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token.getToken(), Duration.ofHours(12)).toString());
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, Constants.XSRF_COOKIE);
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }
        return new DefaultCsrfToken(Constants.XSRF_HEADER, "_csrf", cookie.getValue());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(Constants.XSRF_COOKIE, value)
                .httpOnly(false)
                .secure(appProperties.getCookie().isSecure())
                .sameSite(appProperties.getCookie().getSameSite())
                .path("/")
                .maxAge(maxAge);

        if (StringUtils.hasText(appProperties.getCookie().getDomain())) {
            builder.domain(appProperties.getCookie().getDomain());
        }
        return builder.build();
    }
}
