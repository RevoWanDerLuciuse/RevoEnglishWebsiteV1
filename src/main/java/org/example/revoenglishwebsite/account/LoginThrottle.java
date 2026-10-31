package org.example.revoenglishwebsite.account;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing on the sign-in form: after {@value #MAX_FAILURES} failed attempts from one client
 * address within {@link #WINDOW}, further attempts from that address are refused until the window has passed.
 * Refused attempts never reach the password check, so BCrypt cannot be used to load the server either.
 * <p>
 * Counted per address, not per login: nobody can lock the teacher out of their own account from elsewhere, and
 * the refusal says nothing about whether a login exists. Behind a reverse proxy the address comes from
 * X-Forwarded-For (prod profile), so the proxy must pass it on — otherwise all visitors share the proxy's address.
 * The counters live in memory; a restart clears them.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);
    /** Above this many tracked addresses, expired entries are dropped on the next failure. */
    private static final int CLEANUP_THRESHOLD = 1_000;
    private static final String LOGIN_PATH = "/login";

    private record Failures(Instant firstAt, int count) {
    }

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Whether sign-in attempts from this address are refused right now. */
    public boolean isBlocked(String address) {
        Failures entry = failures.get(address);
        return entry != null && !expired(entry, clock.instant()) && entry.count() >= MAX_FAILURES;
    }

    public void recordFailure(String address) {
        Instant now = clock.instant();
        if (failures.size() > CLEANUP_THRESHOLD) {
            failures.values().removeIf(entry -> expired(entry, now));
        }
        failures.merge(address, new Failures(now, 1),
                (old, first) -> expired(old, now) ? first : new Failures(old.firstAt(), old.count() + 1));
    }

    private static boolean expired(Failures entry, Instant now) {
        return !now.isBefore(entry.firstAt().plus(WINDOW));
    }

    /** Refuses POST /login from a blocked address before Spring Security checks the password. */
    public Filter filter() {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return !"POST".equals(request.getMethod())
                        || !request.getRequestURI().equals(request.getContextPath() + LOGIN_PATH);
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                if (isBlocked(request.getRemoteAddr())) {
                    response.sendRedirect(request.getContextPath() + LOGIN_PATH + "?blocked");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }

    /** Counts the failed attempt, then shows the usual generic error (same for every kind of failure). */
    public AuthenticationFailureHandler failureHandler() {
        SimpleUrlAuthenticationFailureHandler generic = new SimpleUrlAuthenticationFailureHandler(LOGIN_PATH + "?error");
        return (request, response, exception) -> {
            recordFailure(request.getRemoteAddr());
            generic.onAuthenticationFailure(request, response, exception);
        };
    }
}
