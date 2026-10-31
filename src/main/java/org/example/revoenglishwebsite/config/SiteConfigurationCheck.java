package org.example.revoenglishwebsite.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs a clear warning at startup when values that must be set before publishing are missing.
 */
@Component
class SiteConfigurationCheck {

    private static final Logger log = LoggerFactory.getLogger(SiteConfigurationCheck.class);

    private final SiteProperties site;

    SiteConfigurationCheck(SiteProperties site) {
        this.site = site;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnAboutMissingConfiguration() {
        if (!site.indexable()) {
            log.warn("site.base-url (SITE_BASE_URL) is not set: canonical URLs and the sitemap are disabled "
                    + "and robots.txt asks search engines not to index this deployment.");
        }
        if (site.analyticsDebug()) {
            log.warn("site.analytics-debug is enabled: analytics events are logged to the browser console.");
        }
    }
}
