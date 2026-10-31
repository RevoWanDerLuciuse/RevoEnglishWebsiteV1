package org.example.revoenglishwebsite.account;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads accounts for form login. Disabled accounts are returned with {@code enabled = false}, so
 * Spring Security rejects them; the login page shows the same generic message for every failure.
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository accounts;

    public AccountUserDetailsService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        return accounts.findByLogin(AccountRules.normalizeLogin(login))
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown login"));
    }
}
