package org.example.revoenglishwebsite.pricing;

import org.example.revoenglishwebsite.config.SiteProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The monthly plans — the single plan definition for the public site and for student subscriptions.
 * Edit prices here — the cards, savings, Telegram messages and new subscriptions are all derived from
 * this list. Existing subscriptions keep a snapshot of the plan as it was when assigned, so later price
 * changes do not affect them.
 */
@Component
public class PricingCatalog {

    public static final List<PricingPlan> PLANS = List.of(
            new PricingPlan("start", "Start", 4, 1_500,
                    "Раз в неделю — спокойный ритм, чтобы регулярно возвращаться к языку.", false),
            new PricingPlan("regular", "Regular", 8, 1_400,
                    "Два раза в неделю — удобный ритм для постоянной практики.", true),
            new PricingPlan("intensive", "Intensive", 12, 1_300,
                    "Около трёх занятий в неделю — для тех, кто хочет практиковаться чаще.", false));

    /** Plan definition by its id ("start", "regular", "intensive"). */
    public static Optional<PricingPlan> plan(String id) {
        return PLANS.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    private final SiteProperties site;

    public PricingCatalog(SiteProperties site) {
        this.site = site;
    }

    /**
     * Plans with their Telegram links for one page.
     *
     * @param pathName learning path of the current page ("General English", "IT English"), or {@code null}
     */
    public List<PricingOffer> offers(String pathName) {
        return PLANS.stream()
                .map(plan -> new PricingOffer(plan, site.telegramUrl(plan.inquiryMessage(pathName))))
                .toList();
    }

    /** A plan together with the Telegram link that opens a chat with the plan pre-selected. */
    public record PricingOffer(PricingPlan plan, String telegramUrl) {
    }
}
