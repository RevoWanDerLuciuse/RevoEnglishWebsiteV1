package org.example.revoenglishwebsite.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-level checks against the real embedded server: headers, caching, compression,
 * error pages, robots.txt and the health check. Runs without site.base-url, i.e. like a
 * development or preview deployment that must not be indexed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "site.telegram-username=test_teacher")
class ProductionReadinessTests {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void pagesSendSecurityHeadersAndRussianContentLanguage() throws Exception {
        for (String path : SeoController.PUBLIC_PATHS) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(header(response, "Content-Security-Policy")).contains("default-src 'self'", "frame-ancestors 'none'");
            assertThat(header(response, "X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(header(response, "X-Frame-Options")).isEqualTo("DENY");
            assertThat(header(response, "Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
            assertThat(header(response, "Permissions-Policy")).contains("camera=()");
            assertThat(header(response, "Content-Language")).isEqualTo("ru");
        }
    }

    @Test
    void pagesReferenceNoThirdPartyResources() throws Exception {
        for (String path : SeoController.PUBLIC_PATHS) {
            String html = get(path).body();
            assertThat(html).doesNotContainPattern("(src|href)=\"https?://(?!t\\.me/)");
        }
    }

    @Test
    void versionedAssetsAreCachedLongAndScriptsAreRevalidated() throws Exception {
        String html = get("/").body();
        String css = firstMatch(html, "href=\"(/css/site-[0-9a-f]{32}\\.css)\"");

        HttpResponse<String> cssResponse = get(css);
        assertThat(cssResponse.statusCode()).isEqualTo(200);
        assertThat(header(cssResponse, "Cache-Control")).contains("max-age=31536000", "public");

        // Fonts referenced from the stylesheet are rewritten to hashed URLs too
        String font = firstMatch(cssResponse.body(), "url\\(\"?(/fonts/manrope-cyrillic-[0-9a-f]{32}\\.woff2)");
        assertThat(get(font).statusCode()).isEqualTo(200);

        // Every hashed URL referenced by the page must resolve (regression: the favicon once returned 404)
        Matcher hashed = Pattern.compile("(?:href|src)=\"(/[^\"]+-[0-9a-f]{32}\\.[a-z0-9]+)\"").matcher(html);
        int hashedCount = 0;
        while (hashed.find()) {
            hashedCount++;
            assertThat(get(hashed.group(1)).statusCode()).as(hashed.group(1)).isEqualTo(200);
        }
        assertThat(hashedCount).isGreaterThanOrEqualTo(4); // favicon, 2 preloaded fonts, site.css, quiz.css, photo

        HttpResponse<String> js = get("/js/quiz.js");
        assertThat(js.statusCode()).isEqualTo(200);
        assertThat(header(js, "Cache-Control")).contains("no-cache");
        assertThat(header(js, "Last-Modified")).isNotBlank();
    }

    @Test
    void textResponsesAreCompressed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/")).header("Accept-Encoding", "gzip").build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.headers().firstValue("Content-Encoding")).hasValue("gzip");
    }

    @Test
    void unknownPagesShowABrandedNotFoundPageThatIsNotIndexed() throws Exception {
        HttpResponse<String> response = get("/no-such-page", "text/html");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("Такой страницы", "name=\"robots\" content=\"noindex, nofollow\"", "href=\"/english\"", "href=\"/#pricing\"")
                .doesNotContain("Whitelabel", "Exception", "trace");
    }

    @Test
    void errorEndpointRevealsNoInternals() throws Exception {
        HttpResponse<String> html = get("/error", "text/html");
        assertThat(html.body()).contains("Что-то пошло").doesNotContain("Whitelabel", "trace", "exception");

        HttpResponse<String> json = get("/no-such-page", "application/json");
        assertThat(json.statusCode()).isEqualTo(404);
        assertThat(json.body()).doesNotContain("trace", "exception", "message");
    }

    @Test
    void postRequestsWithoutCsrfTokenAreRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/")).POST(HttpRequest.BodyPublishers.ofString("x=1")).build();
        assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void withoutBaseUrlTheSiteAsksNotToBeIndexed() throws Exception {
        assertThat(get("/robots.txt").body()).isEqualTo("User-agent: *\nDisallow: /\n");
        assertThat(get("/sitemap.xml").statusCode()).isEqualTo(404);

        String html = get("/english").body();
        assertThat(html).contains("name=\"robots\" content=\"noindex, nofollow\"", "name=\"twitter:card\" content=\"summary\"",
                "property=\"og:title\"");
        assertThat(html).doesNotContain("rel=\"canonical\"", "og:url", "og:image");
    }

    @Test
    void onlyTheHealthEndpointIsExposed() throws Exception {
        HttpResponse<String> health = get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).isEqualTo("{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}");

        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/beans").statusCode()).isEqualTo(404);
    }

    @Test
    void everyTelegramLinkIsLabelledForAnalytics() throws Exception {
        for (String path : SeoController.PUBLIC_PATHS) {
            Matcher links = Pattern.compile("<a\\b[^>]*href=\"https://t\\.me/[^>]*>").matcher(get(path).body());
            int count = 0;
            while (links.find()) {
                count++;
                assertThat(links.group()).as("Telegram link on %s", path).contains("data-cta=\"");
            }
            assertThat(count).isPositive();
        }
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return get(path, "*/*");
    }

    private HttpResponse<String> get(String path, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).header("Accept", accept).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    private static String firstMatch(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertThat(matcher.find()).as("pattern %s", regex).isTrue();
        return matcher.group(1);
    }
}
