package com.cristian.moneymanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Limits how often one IP can call the public account endpoints
 * (brute-forcing passwords, mass sign-ups, spamming emails).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(int limit, Duration window) {
    }

    private static final Map<String, Rule> RULES = Map.ofEntries(
            Map.entry("/login", new Rule(10, Duration.ofMinutes(5))),
            Map.entry("/register", new Rule(5, Duration.ofHours(1))),
            Map.entry("/forgot-password", new Rule(5, Duration.ofHours(1))),
            Map.entry("/resend-activation", new Rule(5, Duration.ofHours(1))),
            Map.entry("/reset-password", new Rule(10, Duration.ofHours(1))),
            Map.entry("/refresh-token", new Rule(60, Duration.ofMinutes(5))),
            Map.entry("/unsubscribe", new Rule(20, Duration.ofHours(1))),
            Map.entry("/internal/jobs/daily-reminder", new Rule(10, Duration.ofHours(1))),
            Map.entry("/internal/jobs/daily-summary", new Rule(10, Duration.ofHours(1))),
            Map.entry("/internal/jobs/monthly-report", new Rule(10, Duration.ofHours(1))),
            Map.entry("/internal/jobs/recurring", new Rule(10, Duration.ofHours(1)))
    );

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !RULES.containsKey(path(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = path(request);
        Rule rule = RULES.get(path);
        String key = "ip:" + path + ":" + clientIpResolver.resolve(request);

        if (!rateLimiter.tryAcquire(key, rule.limit(), rule.window())) {
            long retryAfter = rateLimiter.secondsUntilReset(key, rule.window());
            log.warn("Rate limit hit on {} from {}", path, clientIpResolver.resolve(request));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Demasiados intentos. Espera "
                    + humanize(retryAfter) + " y vuelve a intentarlo.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Path without the /api/v1.0 context path. */
    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    static String humanize(long seconds) {
        if (seconds < 60) {
            return seconds + " segundos";
        }
        long minutes = (seconds + 59) / 60;
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
