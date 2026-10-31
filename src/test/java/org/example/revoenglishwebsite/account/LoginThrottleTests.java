package org.example.revoenglishwebsite.account;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTests {

    @Test
    void blocksAnAddressAfterTooManyFailuresUntilTheWindowHasPassed() {
        MutableClock clock = new MutableClock();
        LoginThrottle throttle = new LoginThrottle(clock);

        for (int i = 1; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("198.51.100.1");
        }
        assertThat(throttle.isBlocked("198.51.100.1")).isFalse();

        throttle.recordFailure("198.51.100.1");
        assertThat(throttle.isBlocked("198.51.100.1")).isTrue();
        assertThat(throttle.isBlocked("198.51.100.2")).isFalse();

        clock.advance(LoginThrottle.WINDOW);
        assertThat(throttle.isBlocked("198.51.100.1")).isFalse();
        throttle.recordFailure("198.51.100.1"); // a new window starts with a single failure
        assertThat(throttle.isBlocked("198.51.100.1")).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
