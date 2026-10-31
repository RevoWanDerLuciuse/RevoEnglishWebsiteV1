package org.example.revoenglishwebsite.web;

import org.example.revoenglishwebsite.config.SiteProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * robots.txt and sitemap.xml. Until {@code site.base-url} is configured the whole site is
 * disallowed and no sitemap is published, so preview or development deployments are not indexed.
 */
@RestController
public class SeoController {

    /** Public pages listed in the sitemap. */
    static final List<String> PUBLIC_PATHS = List.of("/", "/english", "/it-english");

    private final SiteProperties site;

    public SeoController(SiteProperties site) {
        this.site = site;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        if (!site.indexable()) {
            return "User-agent: *\nDisallow: /\n";
        }
        return """
                User-agent: *
                Allow: /
                Disallow: /actuator/
                Disallow: /error
                Disallow: /login
                Disallow: /dashboard
                Disallow: /account/
                Disallow: /admin/

                Sitemap: %s
                """.formatted(site.absoluteUrl("/sitemap.xml"));
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        if (!site.indexable()) {
            return ResponseEntity.notFound().build();
        }
        String urls = PUBLIC_PATHS.stream()
                .map(path -> "  <url><loc>" + HtmlUtils.htmlEscape(site.absoluteUrl(path)) + "</loc></url>")
                .collect(Collectors.joining("\n"));
        return ResponseEntity.ok("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                %s
                </urlset>
                """.formatted(urls));
    }
}
