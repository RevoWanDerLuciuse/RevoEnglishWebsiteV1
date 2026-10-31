package org.example.revoenglishwebsite.account;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccountRulesTests {

    @Test
    void loginsAreNormalisedAndValidated() {
        assertThat(AccountRules.normalizeLogin("  Ivan.Petrov@Example.COM ")).isEqualTo("ivan.petrov@example.com");
        assertThat(AccountRules.loginError("ivan.petrov@example.com")).isNull();
        assertThat(AccountRules.loginError("anna_k")).isNull();
        assertThat(AccountRules.loginError("ab")).isNotNull();
        assertThat(AccountRules.loginError("иван")).isNotNull();
        assertThat(AccountRules.loginError("a b c")).isNotNull();
        assertThat(AccountRules.loginError(".dot")).isNotNull();
        assertThat(AccountRules.loginError("")).isNotNull();
    }

    @Test
    void displayNamesAreTrimmedAndLimited() {
        assertThat(AccountRules.normalizeDisplayName("  Анна   Иванова ")).isEqualTo("Анна Иванова");
        assertThat(AccountRules.displayNameError("Анна")).isNull();
        assertThat(AccountRules.displayNameError("")).isNotNull();
        assertThat(AccountRules.displayNameError("a".repeat(101))).isNotNull();
    }

    @Test
    void passwordsNeedTenCharactersAndAtMost72Bytes() {
        assertThat(AccountRules.passwordError("long-enough-1", "anna")).isNull();
        assertThat(AccountRules.passwordError("short", "anna")).isNotNull();
        assertThat(AccountRules.passwordError("anna-login", "anna-login")).isNotNull();
        assertThat(AccountRules.passwordError("a".repeat(72), "anna")).isNull();
        assertThat(AccountRules.passwordError("a".repeat(73), "anna")).isNotNull();
        assertThat(AccountRules.passwordError("я".repeat(37), "anna")).isNotNull(); // 74 bytes in UTF-8
    }

    @Test
    void temporaryPasswordsAreRandomAndValid() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String password = AccountRules.generateTemporaryPassword();
            assertThat(password).hasSize(14).matches("[a-zA-Z2-9]+").doesNotContain("0", "O", "1", "l", "I");
            assertThat(AccountRules.passwordError(password, "anna")).isNull();
            seen.add(password);
        }
        assertThat(seen).hasSize(200);
    }
}
