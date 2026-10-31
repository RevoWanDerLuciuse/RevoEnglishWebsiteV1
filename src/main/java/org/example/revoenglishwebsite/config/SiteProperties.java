package org.example.revoenglishwebsite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Public site settings bound from the {@code site.*} properties (see README for the environment variables).
 *
 * @param telegramUsername Telegram username used for every contact link; a leading "@" is ignored
 * @param baseUrl          public origin of the site, e.g. {@code https://example.com}. When blank, the site
 *                         is treated as a non-production deployment: no canonical URLs, no sitemap,
 *                         and search engines are asked not to index it
 * @param analyticsDebug   log analytics events to the browser console (for checking the integration)
 */
@ConfigurationProperties(prefix = "site")
public record SiteProperties(
        String telegramUsername,
        @DefaultValue("") String baseUrl,
        @DefaultValue("false") boolean analyticsDebug) {

    /** Telegram usernames: 5–32 characters, letters, digits and underscores, starting with a letter. */
    private static final Pattern TELEGRAM_USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{4,31}");
    private static final Pattern BASE_URL = Pattern.compile("https?://[A-Za-z0-9.-]+(:\\d+)?(/[A-Za-z0-9._~/-]*)?");

    public SiteProperties {
        Assert.hasText(telegramUsername, "Property 'site.telegram-username' must be set");
        telegramUsername = telegramUsername.strip().replaceFirst("^@", "");
        Assert.isTrue(TELEGRAM_USERNAME.matcher(telegramUsername).matches(),
                "Property 'site.telegram-username' must be a valid Telegram username (5–32 letters, digits or underscores)");

        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceFirst("/+$", "");
        Assert.isTrue(baseUrl.isEmpty() || BASE_URL.matcher(baseUrl).matches(),
                "Property 'site.base-url' must be an absolute http(s) URL such as https://example.com");
    }

    public String telegramUrl() {
        return "https://t.me/" + telegramUsername;
    }

    /**
     * Chat link with a pre-entered draft. Telegram only fills in the input field;
     * the visitor can edit the text and sends it themselves.
     */
    public String telegramUrl(String draftMessage) {
        return telegramUrl() + "?text=" + UriUtils.encodeQueryParam(draftMessage, StandardCharsets.UTF_8);
    }

    public String telegramHandle() {
        return "@" + telegramUsername;
    }

    /** Whether the public domain is configured, i.e. pages may be indexed and canonical URLs built. */
    public boolean indexable() {
        return !baseUrl.isEmpty();
    }

    /** Absolute URL for a site path such as {@code /english}; only meaningful when {@link #indexable()}. */
    public String absoluteUrl(String path) {
        return baseUrl + path;
    }
}
