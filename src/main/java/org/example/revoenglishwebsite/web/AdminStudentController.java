package org.example.revoenglishwebsite.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.revoenglishwebsite.account.AccountException;
import org.example.revoenglishwebsite.account.AccountRules;
import org.example.revoenglishwebsite.account.AccountService;
import org.example.revoenglishwebsite.account.UserAccount;
import org.example.revoenglishwebsite.subscription.SubscriptionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Teacher's account management. Restricted to administrators in SecurityConfig ("/admin/**").
 * Only student accounts can be created or changed here; the role is never taken from the request.
 */
@Controller
@RequestMapping("/admin")
public class AdminStudentController {

    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneOffset.UTC);

    private final AccountService accounts;
    private final SubscriptionService subscriptions;

    public AdminStudentController(AccountService accounts, SubscriptionService subscriptions) {
        this.accounts = accounts;
        this.subscriptions = subscriptions;
    }

    /**
     * Form for a new student. Deliberately has no role field: extra request parameters such as
     * {@code role=ADMIN} are ignored by the binder.
     */
    public static class NewStudentForm {

        @NotBlank(message = "Укажите имя.")
        @Size(max = AccountRules.DISPLAY_NAME_MAX, message = "Имя: до 100 символов.")
        private String displayName = "";

        @NotBlank(message = "Укажите логин.")
        @Size(max = AccountRules.LOGIN_MAX, message = "Логин слишком длинный.")
        private String login = "";

        /** "generate" (default) or "manual" */
        private String passwordMode = "generate";

        @Size(max = 200, message = "Пароль слишком длинный.")
        private String initialPassword = "";

        boolean manualPassword() {
            return "manual".equals(passwordMode);
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getPasswordMode() {
            return passwordMode;
        }

        public void setPasswordMode(String passwordMode) {
            this.passwordMode = passwordMode;
        }

        public String getInitialPassword() {
            return initialPassword;
        }

        public void setInitialPassword(String initialPassword) {
            this.initialPassword = initialPassword;
        }
    }

    /** One row of the student list. */
    public record StudentRow(Long id, String displayName, String login, boolean active, boolean passwordChangeRequired, String created,
                             SubscriptionService.PlanSummary plan) {
        static StudentRow of(UserAccount account, SubscriptionService.PlanSummary plan) {
            return new StudentRow(account.getId(), account.getDisplayName(), account.getLogin(), account.isActive(),
                    account.isPasswordChangeRequired(), CREATED.format(account.getCreatedAt()), plan);
        }
    }

    private List<StudentRow> studentRows() {
        var plans = subscriptions.overviewByStudent();
        return accounts.students().stream().map(a -> StudentRow.of(a, plans.get(a.getId()))).toList();
    }

    @GetMapping({"", "/"})
    public String home() {
        return "redirect:/admin/students";
    }

    @GetMapping("/students")
    public String students(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new NewStudentForm());
        }
        model.addAttribute("students", studentRows());
        return "admin/students";
    }

    @PostMapping("/students")
    public String create(@Valid @ModelAttribute("form") NewStudentForm form, BindingResult errors,
                         Model model, HttpServletRequest request) {
        if (form.manualPassword() && (form.getInitialPassword() == null || form.getInitialPassword().isEmpty())) {
            errors.rejectValue("initialPassword", "required", "Введите пароль или выберите «Сгенерировать».");
        }
        if (!errors.hasErrors()) {
            try {
                AccountService.Credentials created = accounts.createStudent(
                        form.getDisplayName(), form.getLogin(), form.manualPassword() ? form.getInitialPassword() : null);
                return credentialsPage(model, request, created, "Аккаунт ученика создан");
            } catch (AccountException e) {
                errors.rejectValue(e.field() == null ? "login" : e.field(), "invalid", e.getMessage());
            }
        }
        // The template never renders a value into the password field, so a typed password is not sent back.
        model.addAttribute("students", studentRows());
        return "admin/students";
    }

    @PostMapping("/students/{id}/status")
    public String setActive(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
        try {
            var student = accounts.setStudentActive(id, active);
            redirect.addFlashAttribute("notice", (active ? "Доступ включён: " : "Доступ отключён: ") + student.getDisplayName());
        } catch (AccountException e) {
            redirect.addFlashAttribute("notice", e.getMessage());
        }
        return "redirect:/admin/students";
    }

    @PostMapping("/students/{id}/password-reset")
    public String resetPassword(@PathVariable Long id, Model model, HttpServletRequest request, RedirectAttributes redirect) {
        try {
            return credentialsPage(model, request, accounts.resetStudentPassword(id), "Новый временный пароль");
        } catch (AccountException e) {
            redirect.addFlashAttribute("notice", e.getMessage());
            return "redirect:/admin/students";
        }
    }

    /**
     * Shows the sign-in details once, directly in the POST response (not via redirect or session), so a
     * generated password is never stored anywhere. The response is sent with Cache-Control: no-store.
     */
    private String credentialsPage(Model model, HttpServletRequest request, AccountService.Credentials credentials, String title) {
        model.addAttribute("title", title);
        model.addAttribute("student", credentials.account());
        model.addAttribute("temporaryPassword", credentials.temporaryPassword());
        model.addAttribute("loginUrl", ServletUriComponentsBuilder.fromContextPath(request).path("/login").toUriString());
        return "admin/credentials";
    }
}
