package com.ironoak.security;

import com.ironoak.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Per-address request limits on the endpoints an anonymous caller can abuse:
 * staff login and
 * token refresh (password guessing, token guessing), the public write endpoints
 * (spam, stock
 * hoarding, CPU-heavy image classification) and the public id+email lookups
 * (order harvesting).
 * Runs before authentication so that rejected callers cost almost nothing.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger audit = LoggerFactory.getLogger("com.ironoak.security.audit");
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private static final List<String> PUBLIC_WRITE_PATHS = List.of(
            "/api/orders", "/api/complaints", "/api/bookings", "/api/bookings/*/reschedule",
            "/api/bookings/*/cancel", "/api/warranty-claims", "/api/support-tickets",
            "/api/vision/classify", "/api/chat/**");

    private static final List<String> PUBLIC_LOOKUP_PATHS = List.of(
            "/api/orders/*", "/api/bookings", "/api/bookings/*",
            "/api/warranty-claims", "/api/warranty-claims/*");

    private final ClientIpResolver clientIp;
    private final TokenBucketLimiter login;
    private final TokenBucketLimiter refresh;
    private final TokenBucketLimiter publicWrite;
    private final TokenBucketLimiter publicLookup;

    public RateLimitFilter(SecurityProperties properties, ClientIpResolver clientIp) {

        this.clientIp = clientIp;
        SecurityProperties.RateLimit limits = properties.getRateLimit();

        int keys = limits.getMaxTrackedClients();

        this.login = new TokenBucketLimiter(limits.getLoginPerMinute(), keys);
        this.refresh = new TokenBucketLimiter(limits.getRefreshPerMinute(), keys);
        this.publicWrite = new TokenBucketLimiter(limits.getPublicWritePerMinute(), keys);
        this.publicLookup = new TokenBucketLimiter(limits.getPublicLookupPerMinute(), keys);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        TokenBucketLimiter limiter = limiterFor(request);

        if (limiter != null) {

            String ip = clientIp.resolve(request);
            TokenBucketLimiter.Decision decision = limiter.tryAcquire(ip);

            if (!decision.allowed()) {

                audit.warn("rate limit hit: {} {} from {}", request.getMethod(), request.getRequestURI(), ip);

                reject(response, decision.retryAfterSeconds());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private TokenBucketLimiter limiterFor(HttpServletRequest request) {

        String path = request.getRequestURI();
        boolean post = HttpMethod.POST.matches(request.getMethod());

        if (post && path.equals("/api/admin/auth/login")) {
            return login;
        }
        if (post && (path.equals("/api/admin/auth/refresh") || path.equals("/api/admin/auth/logout"))) {
            return refresh;
        }
        if (post && matchesAny(PUBLIC_WRITE_PATHS, path)) {
            return publicWrite;
        }
        if (HttpMethod.GET.matches(request.getMethod()) && matchesAny(PUBLIC_LOOKUP_PATHS, path)) {
            return publicLookup;
        }
        return null;
    }

    private static boolean matchesAny(List<String> patterns, String path) {

        return patterns.stream().anyMatch(pattern -> PATHS.match(pattern, path));
    }

    private static void reject(HttpServletResponse response,
            long retryAfterSeconds) throws IOException {

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                        + "\"detail\":\"Too many requests. Try again later.\"}");
    }
}
