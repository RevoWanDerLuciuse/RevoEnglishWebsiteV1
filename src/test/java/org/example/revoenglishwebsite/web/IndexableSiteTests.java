package org.example.revoenglishwebsite.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behaviour once the production domain is configured: canonical URLs, Open Graph URLs,
 * robots.txt with a sitemap and a sitemap listing every public page.
 */
@SpringBootTest(properties = {"site.telegram-username=test_teacher", "site.base-url=https://example.test/"})
@AutoConfigureMockMvc
class IndexableSiteTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyPageHasItsOwnCanonicalUrlAndSocialMetadata() throws Exception {
        for (String path : SeoController.PUBLIC_PATHS) {
            String html = body(path);
            String url = "https://example.test" + path;

            assertThat(html).contains(
                    "<link rel=\"canonical\" href=\"" + url + "\">",
                    "<meta property=\"og:url\" content=\"" + url + "\">",
                    "<meta property=\"og:image\" content=\"https://example.test/images/emir.jpg\">",
                    "<meta name=\"robots\" content=\"index, follow\">");
        }
    }

    @Test
    void titlesAndDescriptionsAreUnique() throws Exception {
        var titles = SeoController.PUBLIC_PATHS.stream().map(path -> between(body(path), "<title>", "</title>")).distinct().count();
        var descriptions = SeoController.PUBLIC_PATHS.stream()
                .map(path -> between(body(path), "<meta name=\"description\" content=\"", "\"")).distinct().count();

        assertThat(titles).isEqualTo(SeoController.PUBLIC_PATHS.size());
        assertThat(descriptions).isEqualTo(SeoController.PUBLIC_PATHS.size());
    }

    @Test
    void robotsAndSitemapUseTheConfiguredDomain() throws Exception {
        assertThat(body("/robots.txt")).contains("Allow: /", "Disallow: /actuator/", "Sitemap: https://example.test/sitemap.xml");
        assertThat(body("/sitemap.xml")).contains(
                "<loc>https://example.test/</loc>",
                "<loc>https://example.test/english</loc>",
                "<loc>https://example.test/it-english</loc>");
    }

    private String body(String path) {
        try {
            return mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String between(String text, String start, String end) {
        int from = text.indexOf(start) + start.length();
        return text.substring(from, text.indexOf(end, from));
    }
}
