package org.example.revoenglishwebsite.subscription;

/** Roubles in minor units (kopecks). Formatting only — no floating-point arithmetic. */
public final class Money {

    public static final String RUB = "RUB";
    private static final char NBSP = ' ';

    private Money() {
    }

    /** 1120000 → "11 200 ₽", 1120050 → "11 200,50 ₽" (non-breaking spaces). */
    public static String format(long minor) {
        long rubles = Math.abs(minor) / 100;
        long kopecks = Math.abs(minor) % 100;
        String digits = Long.toString(rubles);
        StringBuilder text = new StringBuilder(minor < 0 ? "−" : "");
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                text.append(NBSP);
            }
            text.append(digits.charAt(i));
        }
        if (kopecks != 0) {
            text.append(',').append(kopecks < 10 ? "0" : "").append(kopecks);
        }
        return text.append(NBSP).append('₽').toString();
    }
}
