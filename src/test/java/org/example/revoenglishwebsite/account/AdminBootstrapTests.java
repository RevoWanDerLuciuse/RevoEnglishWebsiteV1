package org.example.revoenglishwebsite.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The administrator bootstrap runs at startup from ADMIN_BOOTSTRAP_* (here given as properties). */
@SpringBootTest(properties = {
        "site.telegram-username=test_teacher",
        "admin.bootstrap.login=Boot-Admin",
        "admin.bootstrap.password=Bootstrap-Pass-42",
        "admin.bootstrap.name=Эмир"})
@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapTests {

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private AccountService accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminBootstrap bootstrap;

    @Test
    void createsTheAdministratorOnStartupWithAHashedPassword() {
        UserAccount admin = repository.findByLogin("boot-admin").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getDisplayName()).isEqualTo("Эмир");
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.isPasswordChangeRequired()).isFalse();
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("Bootstrap-Pass-42");
        assertThat(passwordEncoder.matches("Bootstrap-Pass-42", admin.getPasswordHash())).isTrue();
    }

    @Test
    void runningAgainNeverResetsTheExistingPassword(CapturedOutput output) throws Exception {
        String hashBefore = repository.findByLogin("boot-admin").orElseThrow().getPasswordHash();

        bootstrap.run(null); // e.g. an application restart with the variables still set
        assertThat(accounts.bootstrapAdmin("Эмир", "boot-admin", "Another-Pass-999")).isFalse();

        assertThat(repository.findByLogin("boot-admin").orElseThrow().getPasswordHash()).isEqualTo(hashBefore);
        assertThat(output.getAll()).doesNotContain("Bootstrap-Pass-42").doesNotContain("Another-Pass-999");
    }

    @Test
    void neverPromotesAStudentAndRejectsWeakPasswords() {
        accounts.createStudent("Студент", "not-an-admin", "Student-Pass-123");

        assertThatThrownBy(() -> accounts.bootstrapAdmin("X", "not-an-admin", "Admin-Pass-0001"))
                .isInstanceOf(AccountException.class);
        assertThat(repository.findByLogin("not-an-admin").orElseThrow().getRole()).isEqualTo(Role.STUDENT);

        assertThatThrownBy(() -> accounts.bootstrapAdmin("X", "weak-admin", "short"))
                .isInstanceOf(AccountException.class);
        assertThat(repository.findByLogin("weak-admin")).isEmpty();
    }
}
