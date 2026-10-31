package org.example.revoenglishwebsite.account;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validation and normalisation rules shared by the admin form, the password-change form and the
 * administrator bootstrap. All user-visible messages are Russian.
 */
public final class AccountRules {

    public static final int DISPLAY_NAME_MAX = 100;
    public static final int LOGIN_MIN = 3;
    public static final int LOGIN_MAX = 254;
    public static final int PASSWORD_MIN = 10;
    /** BCrypt only uses the first 72 bytes of a password; longer passwords are rejected instead of silently cut. */
    public static final int PASSWORD_MAX_BYTES = 72;

    /** Username or e-mail: latin letters, digits and . _ - + @, starting with a letter or digit. */
    private static final Pattern LOGIN = Pattern.compile("[a-z0-9][a-z0-9._@+-]{" + (LOGIN_MIN - 1) + "," + (LOGIN_MAX - 1) + "}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");

    /** Unambiguous characters for temporary passwords (no 0/O, 1/l/I). */
    private static final char[] TEMP_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int TEMP_LENGTH = 14;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountRules() {
    }

    public static String normalizeLogin(String login) {
        return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    }

    public static String normalizeDisplayName(String displayName) {
        return displayName == null ? "" : displayName.strip().replaceAll("\\s+", " ");
    }

    /** @return an error message, or {@code null} when the normalised login is valid */
    public static String loginError(String normalizedLogin) {
        if (normalizedLogin.isEmpty()) {
            return "Укажите логин.";
        }
        if (normalizedLogin.length() < LOGIN_MIN || normalizedLogin.length() > LOGIN_MAX || !LOGIN.matcher(normalizedLogin).matches()) {
            return "Логин: от 3 символов — латинские буквы, цифры и символы . _ - + @ (например, ivan.petrov или ivan@example.com).";
        }
        return null;
    }

    /** @return an error message, or {@code null} when the normalised display name is valid */
    public static String displayNameError(String normalizedName) {
        if (normalizedName.isEmpty()) {
            return "Укажите имя.";
        }
        if (normalizedName.length() > DISPLAY_NAME_MAX || CONTROL_CHARS.matcher(normalizedName).find()) {
            return "Имя: до 100 символов, без служебных символов.";
        }
        return null;
    }

    /** @return an error message, or {@code null} when the password is acceptable */
    public static String passwordError(String password, String login) {
        if (password == null || password.length() < PASSWORD_MIN) {
            return "Пароль должен содержать не меньше 10 символов.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            return "Пароль слишком длинный (не больше 72 байт — примерно 72 латинских или 36 русских букв).";
        }
        if (password.strip().isEmpty() || (login != null && password.equalsIgnoreCase(login))) {
            return "Пароль не должен совпадать с логином.";
        }
        return null;
    }

    /** Random temporary password from a cryptographically secure generator. */
    public static String generateTemporaryPassword() {
        char[] chars = new char[TEMP_LENGTH];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = TEMP_ALPHABET[RANDOM.nextInt(TEMP_ALPHABET.length)];
        }
        return new String(chars);
    }
}
