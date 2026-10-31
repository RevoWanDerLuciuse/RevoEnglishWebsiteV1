package org.example.revoenglishwebsite.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SitePropertiesTests {

    @Test
    void normalisesUsernameAndBaseUrl() {
        SiteProperties site = new SiteProperties(" @RevoWanDerLuciuse ", "https://example.test/", false);

        assertThat(site.telegramUrl()).isEqualTo("https://t.me/RevoWanDerLuciuse");
        assertThat(site.telegramHandle()).isEqualTo("@RevoWanDerLuciuse");
        assertThat(site.absoluteUrl("/english")).isEqualTo("https://example.test/english");
        assertThat(site.indexable()).isTrue();
    }

    @Test
    void blankBaseUrlMeansNotIndexable() {
        assertThat(new SiteProperties("RevoWanDerLuciuse", "", false).indexable()).isFalse();
        assertThat(new SiteProperties("RevoWanDerLuciuse", null, false).indexable()).isFalse();
    }

    @Test
    void draftMessageIsUrlEncoded() {
        SiteProperties site = new SiteProperties("RevoWanDerLuciuse", "", false);

        assertThat(site.telegramUrl("Привет & до встречи"))
                .isEqualTo("https://t.me/RevoWanDerLuciuse?text=%D0%9F%D1%80%D0%B8%D0%B2%D0%B5%D1%82%20%26%20%D0%B4%D0%BE%20%D0%B2%D1%81%D1%82%D1%80%D0%B5%D1%87%D0%B8");
    }

    @Test
    void rejectsInvalidTelegramUsernames() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SiteProperties("abc", "", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new SiteProperties("name\"><script>", "", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new SiteProperties("1startsWithDigit", "", false));
    }

    @Test
    void rejectsInvalidBaseUrls() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SiteProperties("RevoWanDerLuciuse", "example.test", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new SiteProperties("RevoWanDerLuciuse", "javascript:alert(1)", false));
        assertThatIllegalArgumentException().isThrownBy(() -> new SiteProperties("RevoWanDerLuciuse", "https://example.test/?q=\"x\"", false));
    }
}
