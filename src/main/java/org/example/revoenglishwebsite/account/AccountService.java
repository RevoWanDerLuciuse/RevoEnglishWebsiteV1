package org.example.revoenglishwebsite.account;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The only place where accounts are created, roles assigned and passwords changed.
 * Plaintext passwords exist only in method parameters and return values; they are hashed
 * before storage and never logged.
 */
@Service
public class AccountService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = Clock.systemUTC();
    }

    /**
     * Result of creating a student or resetting a password. {@code temporaryPassword} is set only when the
     * password was generated, so it can be shown to the teacher exactly once.
     */
    public record Credentials(UserAccount account, String temporaryPassword) {
    }

    @Transactional(readOnly = true)
    public List<UserAccount> students() {
        return accounts.findAllByRoleOrderByCreatedAtDesc(Role.STUDENT);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(Long id) {
        return accounts.findById(id);
    }

    /**
     * Creates an active student who must change the password at first sign-in.
     *
     * @param initialPassword password chosen by the teacher, or {@code null}/blank to generate one
     */
    @Transactional
    public Credentials createStudent(String displayName, String login, String initialPassword) {
        String name = AccountRules.normalizeDisplayName(displayName);
        String normalizedLogin = AccountRules.normalizeLogin(login);
        require("displayName", AccountRules.displayNameError(name));
        require("login", AccountRules.loginError(normalizedLogin));

        boolean generate = initialPassword == null || initialPassword.isEmpty();
        String password = generate ? AccountRules.generateTemporaryPassword() : initialPassword;
        if (!generate) {
            require("initialPassword", AccountRules.passwordError(password, normalizedLogin));
        }
        if (accounts.existsByLogin(normalizedLogin)) {
            throw duplicateLogin();
        }

        UserAccount student = UserAccount.newStudent(name, normalizedLogin, passwordEncoder.encode(password), Instant.now(clock));
        try {
            accounts.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            throw duplicateLogin(); // concurrent creation with the same login (unique constraint)
        }
        return new Credentials(student, generate ? password : null);
    }

    /** Activates or deactivates a student. Administrator accounts cannot be changed here. */
    @Transactional
    public UserAccount setStudentActive(Long studentId, boolean active) {
        UserAccount student = student(studentId);
        student.setActive(active);
        return student;
    }

    /** Generates a new temporary password for a student; they must change it at the next sign-in. */
    @Transactional
    public Credentials resetStudentPassword(Long studentId) {
        UserAccount student = student(studentId);
        String password = AccountRules.generateTemporaryPassword();
        student.replacePassword(passwordEncoder.encode(password), true);
        return new Credentials(student, password);
    }

    /** Changes the signed-in user's own password after checking the current one. */
    @Transactional
    public void changeOwnPassword(Long accountId, String currentPassword, String newPassword) {
        UserAccount account = accounts.findById(accountId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new AccountException(null, "Аккаунт недоступен."));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new AccountException("currentPassword", "Текущий пароль указан неверно.");
        }
        require("newPassword", AccountRules.passwordError(newPassword, account.getLogin()));
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new AccountException("newPassword", "Новый пароль должен отличаться от текущего.");
        }
        account.replacePassword(passwordEncoder.encode(newPassword), false);
    }

    /**
     * Creates the administrator if no account with this login exists. Never changes an existing account,
     * so restarting with the same configuration does not reset the administrator's password.
     *
     * @return {@code true} if a new administrator was created
     */
    @Transactional
    public boolean bootstrapAdmin(String displayName, String login, String password) {
        String normalizedLogin = AccountRules.normalizeLogin(login);
        String name = AccountRules.normalizeDisplayName(displayName);
        require("login", AccountRules.loginError(normalizedLogin));
        require("displayName", AccountRules.displayNameError(name));

        Optional<UserAccount> existing = accounts.findByLogin(normalizedLogin);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                throw new AccountException("login", "This login belongs to a student account; it will not be turned into an administrator.");
            }
            return false;
        }
        require("password", AccountRules.passwordError(password, normalizedLogin));
        accounts.saveAndFlush(UserAccount.newAdmin(name, normalizedLogin, passwordEncoder.encode(password), Instant.now(clock)));
        return true;
    }

    private UserAccount student(Long studentId) {
        return accounts.findByIdAndRole(studentId, Role.STUDENT)
                .orElseThrow(() -> new AccountException(null, "Ученик не найден."));
    }

    private static void require(String field, String error) {
        if (error != null) {
            throw new AccountException(field, error);
        }
    }

    private static AccountException duplicateLogin() {
        return new AccountException("login", "Этот логин уже занят. Выберите другой.");
    }
}
