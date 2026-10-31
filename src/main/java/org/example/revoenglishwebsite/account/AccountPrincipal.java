package org.example.revoenglishwebsite.account;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * The signed-in user as stored in the session. Holds no plaintext password; the hash is erased after
 * authentication. Controllers use {@link #id()} — never an id from the request — to look up "my" account.
 */
public final class AccountPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String login;
    private final String displayName;
    private final Role role;
    private final boolean active;
    private String passwordHash;

    AccountPrincipal(UserAccount account) {
        this.id = account.getId();
        this.login = account.getLogin();
        this.displayName = account.getDisplayName();
        this.role = account.getRole();
        this.active = account.isActive();
        this.passwordHash = account.getPasswordHash();
    }

    public Long id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
