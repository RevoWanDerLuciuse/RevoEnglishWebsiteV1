package org.example.revoenglishwebsite.web;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helpers for checking links in rendered pages.
 */
final class HtmlLinks {

    private static final Pattern HREF = Pattern.compile("href=\"([^\"]*)\"");

    private HtmlLinks() {
    }

    static List<String> hrefs(String html) {
        return HREF.matcher(html).results().map(result -> result.group(1)).toList();
    }

    /** Telegram links of the pricing plans carry their own per-plan draft (see PricingSectionTests). */
    static boolean isPlanLink(String href) {
        return href.contains(org.springframework.web.util.UriUtils.encodeQueryParam("тариф", java.nio.charset.StandardCharsets.UTF_8));
    }

    /** No empty or "#" links, and every same-page anchor points at an existing id. */
    static void assertNoDeadLinksOrAnchors(String html) {
        List<String> hrefs = hrefs(html);
        assertThat(hrefs).doesNotContain("#", "");
        hrefs.stream()
                .filter(href -> href.startsWith("#"))
                .forEach(anchor -> assertThat(html)
                        .as("anchor %s needs a target", anchor)
                        .contains("id=\"" + anchor.substring(1) + "\""));
    }
}
