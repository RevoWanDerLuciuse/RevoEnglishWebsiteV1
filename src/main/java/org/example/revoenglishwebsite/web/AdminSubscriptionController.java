package org.example.revoenglishwebsite.web;

import org.example.revoenglishwebsite.account.AccountPrincipal;
import org.example.revoenglishwebsite.account.Role;
import org.example.revoenglishwebsite.account.UserAccount;
import org.example.revoenglishwebsite.account.UserAccountRepository;
import org.example.revoenglishwebsite.pricing.PricingCatalog;
import org.example.revoenglishwebsite.subscription.BusinessCalendar;
import org.example.revoenglishwebsite.subscription.LessonCreditService;
import org.example.revoenglishwebsite.subscription.Outcome;
import org.example.revoenglishwebsite.subscription.SubscriptionException;
import org.example.revoenglishwebsite.subscription.SubscriptionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Teacher's subscription and lesson-credit management for one student. Restricted to administrators by
 * SecurityConfig ("/admin/**"); the services check the administrator role again and verify that every
 * subscription or ledger entry belongs to the student in the URL.
 */
@Controller
@RequestMapping("/admin/students/{studentId}")
public class AdminSubscriptionController {

    private static final String RESULT = AdminSubscriptionController.class.getName() + ".result.";

    private final SubscriptionService subscriptions;
    private final LessonCreditService credits;
    private final UserAccountRepository accounts;
    private final BusinessCalendar calendar;

    public AdminSubscriptionController(SubscriptionService subscriptions, LessonCreditService credits,
                                       UserAccountRepository accounts, BusinessCalendar calendar) {
        this.subscriptions = subscriptions;
        this.credits = credits;
        this.accounts = accounts;
        this.calendar = calendar;
    }

    @GetMapping("/subscriptions")
    public String page(@PathVariable Long studentId, Model model, HttpSession session) {
        showResult(session, studentId, model);
        UserAccount student = accounts.findByIdAndRole(studentId, Role.STUDENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate today = calendar.today();
        model.addAttribute("student", student);
        model.addAttribute("plans", PricingCatalog.PLANS);
        model.addAttribute("plan", subscriptions.planOf(studentId));
        model.addAttribute("history", subscriptions.historyOf(studentId));
        model.addAttribute("today", today);
        model.addAttribute("defaultExpiry", today.plusMonths(1).minusDays(1));
        // One fresh idempotency key per form; a resubmitted (stale) form reuses its key and is recorded only once
        model.addAttribute("assignKey", UUID.randomUUID().toString());
        model.addAttribute("consumeKey", UUID.randomUUID().toString());
        model.addAttribute("adjustKey", UUID.randomUUID().toString());
        return "admin/subscriptions";
    }

    @PostMapping("/subscriptions")
    public String assign(@PathVariable Long studentId, @AuthenticationPrincipal AccountPrincipal admin,
                         @RequestParam(defaultValue = "") String planCode,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
                         @RequestParam(defaultValue = "false") boolean complimentary,
                         @RequestParam(required = false) String complimentaryReason,
                         @RequestParam(defaultValue = "") String formKey, HttpSession session) {
        return run(studentId, session, () -> {
            Outcome<?> result = subscriptions.assign(studentId, planCode, startsOn, expiresOn,
                    complimentary ? (complimentaryReason == null ? "" : complimentaryReason) : null, admin.id(), formKey);
            return result.created()
                    ? (complimentary ? "Бесплатный абонемент назначен и активирован, занятия начислены." : "Абонемент назначен. Когда получите оплату, нажмите «Оплата получена».")
                    : null;
        });
    }

    /** "Оплата получена": the teacher confirms a payment made outside the site; activates and grants lessons. */
    @PostMapping("/subscriptions/{subscriptionId}/confirm-payment")
    public String confirmPayment(@PathVariable Long studentId, @PathVariable Long subscriptionId,
                                 @AuthenticationPrincipal AccountPrincipal admin, HttpSession session) {
        return run(studentId, session, () -> {
            var result = subscriptions.confirmPayment(studentId, subscriptionId, admin.id());
            return result.created()
                    ? "Оплата подтверждена. Абонемент активен, начислено занятий: " + result.value().getLessonsIncluded() + "."
                    : null;
        });
    }

    @PostMapping("/subscriptions/{subscriptionId}/cancel")
    public String cancel(@PathVariable Long studentId, @PathVariable Long subscriptionId,
                         @AuthenticationPrincipal AccountPrincipal admin,
                         @RequestParam(defaultValue = "") String reason, HttpSession session) {
        return run(studentId, session, () -> {
            subscriptions.cancel(studentId, subscriptionId, reason, admin.id());
            return "Абонемент отменён.";
        });
    }

    @PostMapping("/subscriptions/{subscriptionId}/expire")
    public String expire(@PathVariable Long studentId, @PathVariable Long subscriptionId,
                         @AuthenticationPrincipal AccountPrincipal admin, HttpSession session) {
        return run(studentId, session, () -> {
            subscriptions.markExpired(studentId, subscriptionId, admin.id());
            return "Абонемент отмечен как истёкший.";
        });
    }

    @PostMapping("/subscriptions/{subscriptionId}/adjustments")
    public String adjust(@PathVariable Long studentId, @PathVariable Long subscriptionId,
                         @AuthenticationPrincipal AccountPrincipal admin,
                         @RequestParam(defaultValue = "0") int delta, @RequestParam(defaultValue = "") String reason,
                         @RequestParam(defaultValue = "") String formKey, HttpSession session) {
        return run(studentId, session, () -> credits.adjust(studentId, subscriptionId, delta, reason, admin.id(), formKey).created()
                ? "Корректировка записана." : null);
    }

    /** Manual record of a lesson that took place (until the calendar exists). Not for future dates. */
    @PostMapping("/lessons/consume")
    public String consume(@PathVariable Long studentId, @AuthenticationPrincipal AccountPrincipal admin,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lessonDate,
                          @RequestParam(required = false) String note,
                          @RequestParam(defaultValue = "") String formKey, HttpSession session) {
        return run(studentId, session, () -> {
            if (lessonDate != null && lessonDate.isAfter(calendar.today())) {
                throw new SubscriptionException("Списать можно только прошедшее или сегодняшнее занятие.");
            }
            if (!formKey.matches("[0-9a-f-]{36}")) {
                throw new SubscriptionException("Форма устарела. Обновите страницу и повторите.");
            }
            // "manual-<form key>": unique per submitted form, distinct from future calendar references
            return credits.consumeLesson(studentId, "manual-" + formKey, lessonDate, note, admin.id()).created()
                    ? "Занятие списано." : null;
        });
    }

    @PostMapping("/credits/{transactionId}/restore")
    public String restore(@PathVariable Long studentId, @PathVariable Long transactionId,
                          @AuthenticationPrincipal AccountPrincipal admin,
                          @RequestParam(defaultValue = "") String reason, HttpSession session) {
        return run(studentId, session, () -> credits.restoreLesson(studentId, transactionId, reason, admin.id()).created()
                ? "Занятие возвращено на баланс." : null);
    }

    /**
     * Runs one operation and redirects back to the student's page (POST/redirect/GET).
     * The action returns a success message, or {@code null} when the request had already been recorded.
     */
    private String run(Long studentId, HttpSession session, Supplier<String> action) {
        try {
            String message = action.get();
            storeResult(session, studentId, "notice", message != null ? message
                    : "Эта операция уже была записана — повторная отправка ничего не изменила.");
        } catch (SubscriptionException e) {
            storeResult(session, studentId, "error", e.getMessage());
        }
        return "redirect:/admin/students/" + studentId + "/subscriptions";
    }

    /**
     * The result message of the last operation. Kept as a single session value that a newer result replaces —
     * unlike flash attributes, which queue up after a double submit and then lag one message behind.
     */
    private static void storeResult(HttpSession session, Long studentId, String kind, String message) {
        session.setAttribute(RESULT + studentId, new String[]{kind, message});
    }

    private static void showResult(HttpSession session, Long studentId, Model model) {
        if (session.getAttribute(RESULT + studentId) instanceof String[] result) {
            session.removeAttribute(RESULT + studentId);
            model.addAttribute(result[0], result[1]);
        }
    }
}
