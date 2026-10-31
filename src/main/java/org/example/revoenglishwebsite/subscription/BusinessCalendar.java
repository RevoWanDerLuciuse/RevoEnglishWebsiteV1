package org.example.revoenglishwebsite.subscription;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Today" for subscription dates. The application runs in UTC, but start and expiry dates are calendar days in
 * the business time zone ({@code portal.time-zone}, default Europe/Moscow).
 */
@Component
public class BusinessCalendar {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(@Value("${portal.time-zone:Europe/Moscow}") String zone) {
        this.clock = Clock.systemUTC();
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public Instant now() {
        return clock.instant();
    }

    public ZoneId zone() {
        return zone;
    }
}
