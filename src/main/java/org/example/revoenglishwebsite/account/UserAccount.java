package org.example.revoenglishwebsite.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.Assert;

import java.time.Instant;

/**
 * A portal account: a student created by the teacher, or an administrator.
 * Stores only what is needed to sign in — no contact details, lessons or subscriptions.
 * The schema is defined in db/migration/V1__create_user_account.sql.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, unique = true, length = 254)
    private String login;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
        // for JPA
    }

    private UserAccount(String displayName, String login, String passwordHash, Role role,
                        boolean passwordChangeRequired, Instant createdAt) {
        Assert.hasText(displayName, "displayName is required");
        Assert.hasText(login, "login is required");
        Assert.hasText(passwordHash, "passwordHash is required");
        this.displayName = displayName;
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        this.passwordChangeRequired = passwordChangeRequired;
        this.createdAt = createdAt;
    }

    /** New, active student who must change the initial password at first sign-in. */
    static UserAccount newStudent(String displayName, String login, String passwordHash, Instant now) {
        return new UserAccount(displayName, login, passwordHash, Role.STUDENT, true, now);
    }

    /** Administrator created from the bootstrap configuration; they chose the password themselves. */
    static UserAccount newAdmin(String displayName, String login, String passwordHash, Instant now) {
        return new UserAccount(displayName, login, passwordHash, Role.ADMIN, false, now);
    }

    void setActive(boolean active) {
        this.active = active;
    }

    /** Replaces the password hash; {@code temporary} marks passwords set by the teacher. */
    void replacePassword(String passwordHash, boolean temporary) {
        Assert.hasText(passwordHash, "passwordHash is required");
        this.passwordHash = passwordHash;
        this.passwordChangeRequired = temporary;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLogin() {
        return login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
