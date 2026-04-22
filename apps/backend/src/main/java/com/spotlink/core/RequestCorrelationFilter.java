package com.spotlink.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "spotlink.requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(Constants.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = "sl-" + UUID.randomUUID();
        }

        try {
            MDC.put("requestId", requestId);
            MDC.put("method", request.getMethod());
            MDC.put("path", request.getRequestURI());
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
            response.setHeader(Constants.REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            MDC.remove("method");
            MDC.remove("path");
        }
    }
}
