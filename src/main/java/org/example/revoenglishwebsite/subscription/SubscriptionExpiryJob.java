package org.example.revoenglishwebsite.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Shortly after midnight (business time zone) active subscriptions whose end date has passed become EXPIRED.
 * Until the job runs, such subscriptions are already treated as expired everywhere (see
 * {@link Subscription#isUsableOn} and {@link Subscription#effectiveStatus}), so the timing is not critical.
 */
@Configuration
@EnableScheduling
class SubscriptionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);

    private final SubscriptionService subscriptions;
    private final BusinessCalendar calendar;

    SubscriptionExpiryJob(SubscriptionService subscriptions, BusinessCalendar calendar) {
        this.subscriptions = subscriptions;
        this.calendar = calendar;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "${portal.time-zone:Asia/Ho_Chi_Minh}")
    void expireOverdueSubscriptions() {
        int expired = subscriptions.expireOverdue(calendar.today());
        if (expired > 0) {
            log.info("Marked {} subscription(s) as expired", expired);
        }
    }
}
