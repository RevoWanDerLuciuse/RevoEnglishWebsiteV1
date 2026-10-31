package org.example.revoenglishwebsite.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "site.telegram-username=test_teacher")
@AutoConfigureMockMvc
class PricingSectionTests {

    private static final Pattern PLAN_LINK = Pattern.compile(
            "<a\\b[^>]*href=\"(https://t\\.me/test_teacher\\?text=[^\"]+)\"[^>]*data-cta=\"pricing-(\\w+)\"");

    /** Page path → learning path named in the Telegram draft (null on the homepage). */
    private static final Map<String, String> PAGES = new java.util.LinkedHashMap<>();

    static {
        PAGES.put("/", null);
        PAGES.put("/english", "General English");
        PAGES.put("/it-english", "IT English");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pricingSectionShowsAllPlansWithConsistentFigures() throws Exception {
        for (String path : PAGES.keySet()) {
            String html = render(path);

            assertThat(html).as(path).contains(
                    "id=\"pricing\"", "Абонемент на&nbsp;месяц",
                    "6 000 ₽", "11 200 ₽", "15 600 ₽",
                    "1 500 ₽", "1 400 ₽", "1 300 ₽",
                    "800 ₽", "2 400 ₽", "12 000 ₽", "18 000 ₽",
                    "60 минут", "Базовая цена занятия",
                    "Как работает абонемент", "Обсуждаем до&nbsp;оплаты");
            assertThat(count(html, "class=\"plan__badge\">Рекомендуем<")).as(path).isEqualTo(1);
            assertThat(count(html, "class=\"plan plan--recommended\"")).as(path).isEqualTo(1);
        }
    }

    @Test
    void everyPlanCtaOpensTelegramWithThePlanInTheDraft() throws Exception {
        for (Map.Entry<String, String> page : PAGES.entrySet()) {
            Matcher links = PLAN_LINK.matcher(render(page.getKey()));
            List<String> plans = new java.util.ArrayList<>();
            while (links.find()) {
                String plan = links.group(2);
                plans.add(plan);
                String draft = UriUtils.decode(links.group(1).substring(links.group(1).indexOf("?text=") + 6), StandardCharsets.UTF_8);
                String planText = switch (plan) {
                    case "start" -> "тариф Start — 4 занятия в месяц.";
                    case "regular" -> "тариф Regular — 8 занятий в месяц.";
                    case "intensive" -> "тариф Intensive — 12 занятий в месяц.";
                    default -> throw new AssertionError(plan);
                };
                // One message: on a learning-path page the chosen path is part of the same sentence
                String expected = "Здравствуйте! Меня интересует "
                        + (page.getValue() == null ? "" : page.getValue() + ", ") + planText;
                assertThat(draft).as("%s %s", page.getKey(), plan).isEqualTo(expected);
            }
            assertThat(plans).as(page.getKey()).containsExactly("start", "regular", "intensive");
        }
    }

    @Test
    void learningPathPagesLeadToPricingInsteadOfASeparateMessage() throws Exception {
        for (String path : List.of("/english", "/it-english")) {
            String html = render(path);
            String pathName = PAGES.get(path);

            // hero, exercise and closing contact buttons all point at the plans on the same page
            assertThat(count(html, "href=\"#pricing\"")).as(path).isGreaterThanOrEqualTo(4);
            assertThat(html).as(path)
                    .doesNotContain("data-cta=\"path-hero\"", "data-cta=\"exercise-result\"", "data-cta=\"exercise-feedback\"")
                    .contains("Направление:</span> <strong>" + pathName + "</strong>", "Написать без выбора тарифа");
        }
        // The homepage cards can jump straight to a path's plans
        assertThat(HtmlLinks.hrefs(render("/"))).contains("/english#pricing", "/it-english#pricing");
    }

    @Test
    void navigationLinksToPricing() throws Exception {
        for (String path : PAGES.keySet()) {
            assertThat(HtmlLinks.hrefs(render(path))).as(path).contains("#pricing");
        }
    }

    @Test
    void pricingStylesheetIsServed() throws Exception {
        assertThat(render("/")).containsPattern("href=\"/css/pricing-[0-9a-f]{32}\\.css\"");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static int count(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }
}
