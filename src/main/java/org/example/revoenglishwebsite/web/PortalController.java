package org.example.revoenglishwebsite.web;

import org.example.revoenglishwebsite.account.AccountException;
import org.example.revoenglishwebsite.account.AccountPrincipal;
import org.example.revoenglishwebsite.account.AccountService;
import org.example.revoenglishwebsite.account.PortalLoginSuccessHandler;
import org.example.revoenglishwebsite.account.UserAccount;
import org.example.revoenglishwebsite.subscription.SubscriptionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Sign-in page, the student dashboard and the own-password page.
 * The current user always comes from the security context — no account id is ever read from the request.
 */
@Controller
public class PortalController {

    private final AccountService accounts;
    private final SubscriptionService subscriptions;

    public PortalController(AccountService accounts, SubscriptionService subscriptions) {
        this.accounts = accounts;
        this.subscriptions = subscriptions;
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal AccountPrincipal principal) {
        if (principal != null) {
            return "redirect:" + PortalLoginSuccessHandler.homeOf(principal.role());
        }
        return "portal/login";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AccountPrincipal principal, Model model) {
        UserAccount me = accounts.findById(principal.id()).orElseThrow();
        model.addAttribute("me", me);
        // Only the signed-in student's own subscription, looked up by the id from the security context
        model.addAttribute("plan", subscriptions.planOf(principal.id()));
        return "portal/dashboard";
    }

    @GetMapping("/account/password")
    public String passwordForm(@AuthenticationPrincipal AccountPrincipal principal, Model model) {
        addPasswordPageModel(principal, model);
        return "portal/password";
    }

    @PostMapping("/account/password")
    public String changePassword(@AuthenticationPrincipal AccountPrincipal principal,
                                 @RequestParam(defaultValue = "") String currentPassword,
                                 @RequestParam(defaultValue = "") String newPassword,
                                 @RequestParam(defaultValue = "") String confirmPassword,
                                 Model model, RedirectAttributes redirect) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                throw new AccountException("confirmPassword", "Пароли не совпадают.");
            }
            accounts.changeOwnPassword(principal.id(), currentPassword, newPassword);
        } catch (AccountException e) {
            addPasswordPageModel(principal, model);
            model.addAttribute("errorField", e.field());
            model.addAttribute("errorMessage", e.getMessage());
            return "portal/password";
        }
        redirect.addFlashAttribute("notice", "Пароль изменён.");
        return "redirect:" + PortalLoginSuccessHandler.homeOf(principal.role());
    }

    private void addPasswordPageModel(AccountPrincipal principal, Model model) {
        UserAccount me = accounts.findById(principal.id()).orElseThrow();
        model.addAttribute("me", me);
        model.addAttribute("home", PortalLoginSuccessHandler.homeOf(me.getRole()));
    }
}
