package com.senamed.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senamed.backend.common.ApiError;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting for {@code POST /api/public/appointments} (KAN-58, RF-025-adjacent abuse
 * protection) - patients could otherwise script-flood the public booking endpoint. Buckets are
 * kept in-memory per application instance; moving to a shared store (e.g. Redis) is only needed
 * once this app runs behind more than one instance, since each instance would otherwise enforce
 * its own independent limit.
 *
 * <p>Capacity/window are constructor-configurable (not just constants) because integration tests
 * share a single cached Spring context - and therefore a single {@code bucketsByIp} instance and
 * the same loopback "IP" - across multiple test classes that each book several appointments; the
 * production default of 8/minute is tight enough that this shared state alone can trip a 429 that
 * has nothing to do with the behavior under test. The "test" profile raises this to effectively
 * unlimited (see {@code application-test.properties}).</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LIMITED_PATH = "/api/public/appointments";

    private final ObjectMapper objectMapper;
    private final int capacityPerWindow;
    private final Duration refillWindow;
    private final Map<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper, int capacityPerWindow, Duration refillWindow) {
        this.objectMapper = objectMapper;
        this.capacityPerWindow = capacityPerWindow;
        this.refillWindow = refillWindow;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!isRateLimitedRequest(request) || consumeToken(clientIp(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                "Muitas tentativas de agendamento em pouco tempo. Aguarde um instante e tente novamente.");
        objectMapper.writeValue(response.getWriter(), error);
    }

    private boolean isRateLimitedRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && LIMITED_PATH.equals(request.getRequestURI());
    }

    private boolean consumeToken(String ip) {
        return bucketsByIp.computeIfAbsent(ip, key -> newBucket()).tryConsume(1);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder().capacity(capacityPerWindow).refillGreedy(capacityPerWindow, refillWindow).build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
