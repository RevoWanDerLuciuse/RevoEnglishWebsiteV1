package org.example.revoenglishwebsite.subscription;

import org.example.revoenglishwebsite.account.Role;
import org.example.revoenglishwebsite.account.UserAccount;
import org.example.revoenglishwebsite.account.UserAccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Second line of defence behind the URL rules in SecurityConfig: every subscription or credit change re-checks
 * in the service that the acting account exists, is active and is an administrator.
 */
@Component
class AdminGuard {

    private final UserAccountRepository accounts;

    AdminGuard(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    void requireAdmin(Long actorId) {
        boolean admin = actorId != null && accounts.findById(actorId)
                .filter(UserAccount::isActive)
                .filter(a -> a.getRole() == Role.ADMIN)
                .isPresent();
        if (!admin) {
            throw new AccessDeniedException("Only an administrator may change subscriptions or lesson credits");
        }
    }

    UserAccount requireStudent(Long studentId) {
        return accounts.findByIdAndRole(studentId, Role.STUDENT)
                .orElseThrow(() -> new SubscriptionException("Ученик не найден."));
    }
}
