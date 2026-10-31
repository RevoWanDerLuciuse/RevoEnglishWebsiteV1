package org.example.revoenglishwebsite.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "site.telegram-username=@test_teacher")
@AutoConfigureMockMvc
class LearningPathControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generalEnglishPageRendersAllSections() throws Exception {
        String html = render("/english", "english");

        assertThat(html).contains(
                "id=\"problems\"", "id=\"areas\"", "id=\"exercise\"", "id=\"lessons\"", "id=\"contact\"",
                "data-quiz=\"general-english\"", "src=\"/js/exercises/general-english.js\"",
                "aria-current=\"page\">General English</a>");
    }

    @Test
    void itEnglishPageRendersAllSections() throws Exception {
        String html = render("/it-english", "it-english");

        assertThat(html).contains(
                "id=\"scenarios\"", "id=\"scenario\"", "id=\"lessons\"", "id=\"contact\"",
                "data-quiz=\"it-english\"", "src=\"/js/exercises/it-english.js\"",
                "aria-current=\"page\">IT English</a>");
    }

    @Test
    void pagesUseContextualTelegramDrafts() throws Exception {
        assertContextualTelegramLinks(render("/english", "english"), LearningPathController.GENERAL_ENGLISH_DRAFT);
        assertContextualTelegramLinks(render("/it-english", "it-english"), LearningPathController.IT_ENGLISH_DRAFT);
    }

    @Test
    void pagesLinkHomeAndToEachOther() throws Exception {
        String general = render("/english", "english");
        String it = render("/it-english", "it-english");

        assertThat(HtmlLinks.hrefs(general)).contains("/", "/#paths", "/it-english");
        assertThat(HtmlLinks.hrefs(it)).contains("/", "/#paths", "/english");
        HtmlLinks.assertNoDeadLinksOrAnchors(general);
        HtmlLinks.assertNoDeadLinksOrAnchors(it);
    }

    @Test
    void exerciseScriptsAreServed() throws Exception {
        String general = script("/js/exercises/general-english.js");
        String it = script("/js/exercises/it-english.js");

        assertThat(Pattern.compile("\\banswer: [0-2],").matcher(general).results().count()).isEqualTo(4);
        assertThat(Pattern.compile("\\banswer: [0-2],").matcher(it).results().count()).isEqualTo(1);
        assertThat(it).contains("notes: [", "patterns: [");
        mockMvc.perform(get("/css/paths.css")).andExpect(status().isOk());
    }

    private void assertContextualTelegramLinks(String html, String draft) {
        String contextual = "https://t.me/test_teacher?text="
                + UriUtils.encodeQueryParam(draft, StandardCharsets.UTF_8);

        assertThat(HtmlLinks.hrefs(html))
                .filteredOn(href -> href.contains("t.me") && !HtmlLinks.isPlanLink(href))
                .isNotEmpty()
                .allMatch(href -> href.equals(contextual) || href.equals("https://t.me/test_teacher"))
                .contains(contextual);
    }

    private String render(String path, String viewName) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(view().name(viewName))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String script(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
