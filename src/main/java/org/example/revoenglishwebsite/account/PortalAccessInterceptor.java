package org.example.revoenglishwebsite.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Runs on every private page (/dashboard, /admin), after Spring Security has checked the role:
 * <ul>
 *   <li>re-reads the account, so a deactivated or deleted account loses access immediately,
 *       not only when its session expires;</li>
 *   <li>sends users with a temporary password to the password-change page first.</li>
 * </ul>
 */
@Component
public class PortalAccessInterceptor implements HandlerInterceptor {

    private final AccountService accounts;

    public PortalAccessInterceptor(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            return true; // Spring Security has already rejected anonymous access to these paths
        }
        Optional<UserAccount> account = accounts.findById(principal.id()).filter(UserAccount::isActive);
        if (account.isEmpty()) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            response.sendRedirect(request.getContextPath() + "/login?ended");
            return false;
        }
        if (account.get().isPasswordChangeRequired()) {
            response.sendRedirect(request.getContextPath() + "/account/password");
            return false;
        }
        return true;
    }
}
