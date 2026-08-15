package com.rupi.security;

import com.rupi.service.RateLimiterService;
import com.rupi.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;

    public RateLimitFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/status")
                || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume(user.userId());
        if (!result.allowed()) {
            writeRateLimited(response, result.retryAfterSeconds());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void writeRateLimited(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        String requestId = Optional.ofNullable(MDC.get(RequestIdFilter.MDC_KEY))
                .filter(id -> !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(
                        "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Retry after "
                                + retryAfterSeconds
                                + " seconds.\",\"requestId\":\""
                                + requestId
                                + "\",\"fieldErrors\":[]}");
    }
}
