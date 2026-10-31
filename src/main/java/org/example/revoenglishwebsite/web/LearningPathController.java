package org.example.revoenglishwebsite.web;

import org.example.revoenglishwebsite.config.SiteProperties;
import org.example.revoenglishwebsite.pricing.PricingCatalog;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Dedicated pages for the two learning paths.
 */
@Controller
public class LearningPathController {

    static final String GENERAL_ENGLISH_DRAFT =
            "Здравствуйте, Эмир! Меня интересует General English. Хочу обсудить занятия.";
    static final String IT_ENGLISH_DRAFT =
            "Здравствуйте, Эмир! Меня интересует IT English. Хочу обсудить занятия.";

    private final SiteProperties site;
    private final PricingCatalog pricing;

    public LearningPathController(SiteProperties site, PricingCatalog pricing) {
        this.site = site;
        this.pricing = pricing;
    }

    @GetMapping("/english")
    public String generalEnglish(Model model) {
        addContact(model, GENERAL_ENGLISH_DRAFT);
        addPricing(model, "General English");
        return "english";
    }

    @GetMapping("/it-english")
    public String itEnglish(Model model) {
        addContact(model, IT_ENGLISH_DRAFT);
        addPricing(model, "IT English");
        return "it-english";
    }

    /** Plans whose Telegram drafts already name this path; the page sends visitors here to choose a plan. */
    private void addPricing(Model model, String pathName) {
        model.addAttribute("pricingPath", pathName);
        model.addAttribute("pricingOffers", pricing.offers(pathName));
    }

    private void addContact(Model model, String draft) {
        model.addAttribute("telegramDraft", draft);
        model.addAttribute("telegramContactUrl", site.telegramUrl(draft));
    }
}
