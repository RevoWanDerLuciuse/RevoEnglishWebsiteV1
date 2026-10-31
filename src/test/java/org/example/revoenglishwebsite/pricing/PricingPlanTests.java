package org.example.revoenglishwebsite.pricing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PricingPlanTests {

    private static final List<PricingPlan> PLANS = PricingCatalog.PLANS;

    @Test
    void plansMatchTheAgreedPrices() {
        assertThat(PLANS).extracting(PricingPlan::name).containsExactly("Start", "Regular", "Intensive");
        assertThat(PLANS).extracting(PricingPlan::lessonsPerMonth).containsExactly(4, 8, 12);
        assertThat(PLANS).extracting(PricingPlan::pricePerLesson).containsExactly(1_500, 1_400, 1_300);
        assertThat(PLANS).extracting(PricingPlan::monthlyPrice).containsExactly(6_000, 11_200, 15_600);
        assertThat(PLANS).extracting(PricingPlan::hourlyRate).containsExactly(1_500, 1_400, 1_300);
    }

    @Test
    void savingsAreMeasuredAgainstTheStandardLessonPrice() {
        assertThat(PLANS).extracting(PricingPlan::standardMonthlyPrice).containsExactly(6_000, 12_000, 18_000);
        assertThat(PLANS).extracting(PricingPlan::monthlySavings).containsExactly(0, 800, 2_400);
        assertThat(PLANS).extracting(PricingPlan::discounted).containsExactly(false, true, true);
    }

    @Test
    void onlyRegularIsRecommended() {
        assertThat(PLANS).filteredOn(PricingPlan::recommended).extracting(PricingPlan::name).containsExactly("Regular");
    }

    @Test
    void displayTextsUseNonBreakingSpaces() {
        PricingPlan regular = PLANS.get(1);

        assertThat(regular.monthlyPriceText()).isEqualTo("11 200 ₽");
        assertThat(regular.monthlySavingsText()).isEqualTo("800 ₽");
        assertThat(regular.standardMonthlyPriceText()).isEqualTo("12 000 ₽");
        assertThat(regular.hourlyRateText()).isEqualTo("1 400 ₽");
        assertThat(regular.lessonDurationText()).isEqualTo("60 минут");
        assertThat(PricingPlan.rubles(1_234_567)).isEqualTo("1 234 567 ₽");
    }

    @Test
    void inquiryMessagesNameThePlanWithCorrectRussianPlurals() {
        assertThat(PLANS.get(0).inquiryMessage(null)).isEqualTo("Здравствуйте! Меня интересует тариф Start — 4 занятия в месяц.");
        assertThat(PLANS.get(1).inquiryMessage(null)).isEqualTo("Здравствуйте! Меня интересует тариф Regular — 8 занятий в месяц.");
        assertThat(PLANS.get(2).inquiryMessage(null)).isEqualTo("Здравствуйте! Меня интересует тариф Intensive — 12 занятий в месяц.");
        assertThat(PLANS.get(1).inquiryMessage("IT English"))
                .isEqualTo("Здравствуйте! Меня интересует IT English, тариф Regular — 8 занятий в месяц.");

        assertThat(PricingPlan.lessonsWord(1)).isEqualTo("занятие");
        assertThat(PricingPlan.lessonsWord(3)).isEqualTo("занятия");
        assertThat(PricingPlan.lessonsWord(11)).isEqualTo("занятий");
        assertThat(PricingPlan.lessonsWord(22)).isEqualTo("занятия");
    }

    @Test
    void rejectsPricesAboveTheStandardRate() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PricingPlan("x", "X", 4, 1_600, "", false));
    }
}
