package org.example.revoenglishwebsite.web;

import org.example.revoenglishwebsite.config.SiteProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Model attributes shared by every page (header, footer, SEO metadata and generic contact links).
 */
@ControllerAdvice
public class SiteModelAdvice {

    private final SiteProperties site;

    public SiteModelAdvice(SiteProperties site) {
        this.site = site;
    }

    @ModelAttribute("telegramUrl")
    public String telegramUrl() {
        return site.telegramUrl();
    }

    @ModelAttribute("telegramHandle")
    public String telegramHandle() {
        return site.telegramHandle();
    }

    @ModelAttribute("siteBaseUrl")
    public String siteBaseUrl() {
        return site.baseUrl();
    }

    @ModelAttribute("siteIndexable")
    public boolean siteIndexable() {
        return site.indexable();
    }

    @ModelAttribute("analyticsDebug")
    public boolean analyticsDebug() {
        return site.analyticsDebug();
    }
}
