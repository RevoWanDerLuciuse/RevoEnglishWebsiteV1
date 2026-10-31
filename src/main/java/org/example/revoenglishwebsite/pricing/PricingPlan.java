package org.example.revoenglishwebsite.pricing;

import org.springframework.util.Assert;

/**
 * A monthly lesson plan. Only the lesson count and the price per lesson are stored;
 * every other displayed value (monthly price, savings, hourly rate) is derived from them,
 * so the numbers on the page can never contradict each other.
 *
 * @param id              stable identifier used in anchors and analytics labels
 * @param name            plan name shown on the card
 * @param lessonsPerMonth number of individual lessons included in one month
 * @param pricePerLesson  price of one lesson in roubles
 * @param audience        who the plan suits (Russian)
 * @param recommended     whether the card is visually emphasised
 */
public record PricingPlan(String id, String name, int lessonsPerMonth, int pricePerLesson,
                          String audience, boolean recommended) {

    /** Price of a single lesson without a subscription discount, in roubles. */
    public static final int STANDARD_LESSON_PRICE = 1_500;
    /** Every lesson is 60 minutes long. */
    public static final int LESSON_MINUTES = 60;
    /** All prices are in Russian roubles. */
    public static final String CURRENCY = "RUB";

    private static final char NBSP = ' ';

    public PricingPlan {
        Assert.hasText(id, "id is required");
        Assert.hasText(name, "name is required");
        Assert.isTrue(lessonsPerMonth > 0, "lessonsPerMonth must be positive");
        Assert.isTrue(pricePerLesson > 0 && pricePerLesson <= STANDARD_LESSON_PRICE,
                "pricePerLesson must be positive and not above the standard price");
    }

    public int monthlyPrice() {
        return lessonsPerMonth * pricePerLesson;
    }

    /** Monthly price in minor units (kopecks) — the form stored with subscriptions; never a floating-point value. */
    public long monthlyPriceMinor() {
        return monthlyPrice() * 100L;
    }

    /** What the same number of lessons would cost at the standard single-lesson price. */
    public int standardMonthlyPrice() {
        return lessonsPerMonth * STANDARD_LESSON_PRICE;
    }

    public int monthlySavings() {
        return standardMonthlyPrice() - monthlyPrice();
    }

    public boolean discounted() {
        return monthlySavings() > 0;
    }

    /** Effective price of one hour of tuition. */
    public int hourlyRate() {
        return pricePerLesson * 60 / LESSON_MINUTES;
    }

    public int lessonMinutes() {
        return LESSON_MINUTES;
    }

    // ----- Russian display texts -----

    public String monthlyPriceText() {
        return rubles(monthlyPrice());
    }

    public String standardMonthlyPriceText() {
        return rubles(standardMonthlyPrice());
    }

    public String monthlySavingsText() {
        return rubles(monthlySavings());
    }

    public String hourlyRateText() {
        return rubles(hourlyRate());
    }

    /** "60 минут" */
    public String lessonDurationText() {
        return LESSON_MINUTES + " минут";
    }

    public String standardLessonPriceText() {
        return rubles(STANDARD_LESSON_PRICE);
    }

    /** "4 занятия", "8 занятий", … */
    public String lessonsText() {
        return lessonsPerMonth + " " + lessonsWord(lessonsPerMonth);
    }

    /**
     * Draft for the Telegram chat. On the homepage: «Здравствуйте! Меня интересует тариф Regular — 8 занятий в месяц.»
     * On a learning-path page the chosen path is part of the same sentence:
     * «Здравствуйте! Меня интересует IT English, тариф Regular — 8 занятий в месяц.»
     *
     * @param pathName learning path the visitor has chosen, or {@code null} on the homepage
     */
    public String inquiryMessage(String pathName) {
        String subject = pathName == null ? "тариф " : pathName + ", тариф ";
        return "Здравствуйте! Меня интересует " + subject + name + " — " + lessonsText() + " в месяц.";
    }

    /** Formats roubles with non-breaking thousand separators: 11 200 ₽. */
    static String rubles(int amount) {
        String digits = Integer.toString(amount);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                grouped.append(NBSP);
            }
            grouped.append(digits.charAt(i));
        }
        return grouped.append(NBSP).append('₽').toString();
    }

    static String lessonsWord(int count) {
        int lastTwo = count % 100;
        int last = count % 10;
        if (last == 1 && lastTwo != 11) {
            return "занятие";
        }
        if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) {
            return "занятия";
        }
        return "занятий";
    }
}
