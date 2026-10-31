package org.example.revoenglishwebsite.subscription;

import java.util.regex.Pattern;

/** Validation of free-text and key inputs shared by the subscription services. */
final class Inputs {

    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern LESSON_REF = Pattern.compile("[a-z0-9][a-z0-9:_-]{0,99}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\r\\t]]");

    private Inputs() {
    }

    /**
     * Idempotency key of a submitted form: the UUID rendered into the form. Resubmitting the same form
     * reuses the key, so the operation is recorded only once.
     */
    static String requestKey(String prefix, String uuid) {
        if (uuid == null || !UUID.matcher(uuid).matches()) {
            throw new SubscriptionException("Форма устарела. Обновите страницу и повторите.");
        }
        return prefix + ":" + uuid;
    }

    /** Required reason/comment: 3–500 characters. */
    static String reason(String text, String what) {
        String value = optionalText(text);
        if (value == null || value.length() < 3) {
            throw new SubscriptionException("Укажите " + what + " (не меньше 3 символов).");
        }
        return value;
    }

    /** Optional note: trimmed, at most 500 characters, {@code null} when empty. */
    static String optionalText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip();
        if (value.length() > 500 || CONTROL_CHARS.matcher(value).find()) {
            throw new SubscriptionException("Текст: не больше 500 символов, без служебных символов.");
        }
        return value;
    }

    static String lessonRef(String ref) {
        if (ref == null || !LESSON_REF.matcher(ref).matches()) {
            throw new SubscriptionException("Некорректная ссылка на занятие.");
        }
        return ref;
    }
}
