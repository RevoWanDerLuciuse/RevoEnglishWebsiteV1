package org.example.revoenglishwebsite.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "site.telegram-username=@test_teacher")
@AutoConfigureMockMvc
class HomeControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePageIsPublicAndRendersAllSections() throws Exception {
        String html = renderHomePage();

        assertThat(html).contains(
                "id=\"paths\"", "id=\"approach\"", "id=\"about\"", "id=\"contact\"",
                "General English", "IT English", "@test_teacher");
    }

    @Test
    void practiceSectionLoadsQuizAssets() throws Exception {
        String html = renderHomePage();

        assertThat(html).contains(
                "id=\"practice\"", "data-quiz=\"natural-english\"",
                "src=\"/js/exercises/natural-english.js\"",
                "data-quiz-next", "data-quiz-restart");
        assertThat(html).containsPattern("href=\"/css/quiz-[0-9a-f]{32}\\.css\"");
    }

    @Test
    void naturalEnglishExerciseHasFiveQuestions() throws Exception {
        String script = mockMvc.perform(get("/js/exercises/natural-english.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(Pattern.compile("\\banswer: [0-2],").matcher(script).results().count()).isEqualTo(5);
        assertThat(script).contains("import { createQuiz } from '../quiz.js'");
    }

    @Test
    void aboutSectionShowsTeacherWithoutPlaceholders() throws Exception {
        String html = renderHomePage();

        assertThat(html).contains("Эмир");
        // static assets get content-hashed URLs, e.g. /images/emir-<hash>.jpg
        assertThat(html).containsPattern("src=\"/images/emir-[0-9a-f]{32}\\.jpg\"");
        assertThat(html).doesNotContain("placeholder", "[Ваше имя]");
    }

    @Test
    void everyTelegramLinkUsesConfiguredUsername() throws Exception {
        List<String> telegramLinks = HtmlLinks.hrefs(renderHomePage()).stream()
                .filter(href -> href.contains("t.me") && !HtmlLinks.isPlanLink(href))
                .toList();

        assertThat(telegramLinks).isNotEmpty().allMatch("https://t.me/test_teacher"::equals);
    }

    @Test
    void noDeadLinksAndEveryAnchorHasATarget() throws Exception {
        String html = renderHomePage();

        HtmlLinks.assertNoDeadLinksOrAnchors(html);
        assertThat(HtmlLinks.hrefs(html)).contains("#paths");
    }

    @Test
    void homePageLinksToBothLearningPaths() throws Exception {
        assertThat(HtmlLinks.hrefs(renderHomePage())).contains("/english", "/it-english");
    }

    @Test
    void staticAssetsArePublic() throws Exception {
        mockMvc.perform(get("/css/site.css"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/css/quiz.css"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/js/quiz.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/images/emir.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    private String renderHomePage() throws Exception {
        return mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
