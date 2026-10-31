package org.example.revoenglishwebsite.subscription;

import org.example.revoenglishwebsite.account.Role;
import org.example.revoenglishwebsite.account.UserAccount;
import org.example.revoenglishwebsite.account.UserAccountRepository;
import org.example.revoenglishwebsite.pricing.PricingCatalog;
import org.example.revoenglishwebsite.pricing.PricingPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Subscriptions: assignment by the teacher, manual payment records, explicit activation, cancellation and expiry,
 * plus the read models for the admin page and the student dashboard.
 * <p>
 * Every change runs in one database transaction: e.g. activation and the credit grant succeed or fail together.
 */
@Service
public class SubscriptionService {

    /** Longest allowed subscription period. */
    static final int MAX_DAYS = 366;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final SubscriptionRepository subscriptions;
    private final SubscriptionPaymentRepository payments;
    private final LessonCreditService credits;
    private final UserAccountRepository accounts;
    private final AdminGuard guard;
    private final BusinessCalendar calendar;

    public SubscriptionService(SubscriptionRepository subscriptions, SubscriptionPaymentRepository payments,
                               LessonCreditService credits, UserAccountRepository accounts, AdminGuard guard,
                               BusinessCalendar calendar) {
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.credits = credits;
        this.accounts = accounts;
        this.guard = guard;
        this.calendar = calendar;
    }

    // ----- changes (administrators only) -----

    /**
     * Assigns a plan. A paid subscription starts as PENDING_PAYMENT and grants nothing until it is activated.
     * A complimentary one (reason required) is free and becomes ACTIVE at once, with its lessons granted.
     *
     * @param complimentaryReason {@code null} for a paid subscription
     * @param formKey             UUID of the submitted form; a resubmission returns the existing subscription
     */
    @Transactional
    public Outcome<Subscription> assign(Long studentId, String planCode, LocalDate startsOn, LocalDate expiresOn,
                                        String complimentaryReason, Long adminId, String formKey) {
        guard.requireAdmin(adminId);
        String key = Inputs.requestKey("assign", formKey);
        Optional<Subscription> existing = subscriptions.findByRequestKey(key);
        if (existing.isPresent()) {
            return new Outcome<>(owned(existing.get(), studentId), false);
        }
        lockStudent(studentId);
        PricingPlan plan = PricingCatalog.plan(planCode).orElseThrow(() -> new SubscriptionException("Выберите тариф."));
        validatePeriod(startsOn, expiresOn);
        String reason = complimentaryReason == null ? null : Inputs.reason(complimentaryReason, "причину бесплатного абонемента");

        Subscription subscription = subscriptions.saveAndFlush(
                Subscription.fromPlan(studentId, plan, startsOn, expiresOn, reason, adminId, calendar.now(), key));
        if (subscription.isComplimentary()) {
            activateLocked(subscription, adminId);
        }
        return new Outcome<>(subscription, true);
    }

    /**
     * The teacher's "payment received" click. Payment itself happens outside the site; this records that it was
     * confirmed (plan price, today, by whom — for the history) and activates the subscription, granting its lessons,
     * all in one transaction. Clicking twice changes nothing.
     */
    @Transactional
    public Outcome<Subscription> confirmPayment(Long studentId, Long subscriptionId, Long adminId) {
        guard.requireAdmin(adminId);
        Subscription subscription = lockOwned(studentId, subscriptionId);
        String key = "confirm:" + subscription.getId();
        if (payments.findByRequestKey(key).isPresent()) {
            return new Outcome<>(subscription, false);
        }
        if (subscription.isComplimentary()) {
            throw new SubscriptionException("Бесплатный абонемент не требует оплаты.");
        }
        if (subscription.getStatus() != SubscriptionStatus.PENDING_PAYMENT) {
            throw new SubscriptionException("Подтвердить оплату можно только для абонемента, ожидающего оплаты.");
        }
        if (subscription.getExpiresOn().isBefore(calendar.today())) {
            throw new SubscriptionException("Срок этого абонемента уже закончился. Отмените его и назначьте новый.");
        }
        payments.saveAndFlush(new SubscriptionPayment(subscription.getId(), subscription.getPriceMinor(), calendar.today(),
                null, adminId, calendar.now(), key));
        lockStudent(studentId);
        activateLocked(subscription, adminId); // if this fails (e.g. another subscription in force), nothing is kept
        return new Outcome<>(subscription, true);
    }

    /** Marks a subscription as cancelled; any refund is arranged by the teacher outside the site. */
    @Transactional
    public Subscription cancel(Long studentId, Long subscriptionId, String comment, Long adminId) {
        guard.requireAdmin(adminId);
        String note = Inputs.optionalText(comment);
        Subscription subscription = lockOwned(studentId, subscriptionId);
        subscription.cancel(note, adminId, calendar.now());
        return subscriptions.saveAndFlush(subscription);
    }

    /** Marks an active subscription whose end date has passed as expired (the daily job does the same). */
    @Transactional
    public Subscription markExpired(Long studentId, Long subscriptionId, Long adminId) {
        guard.requireAdmin(adminId);
        Subscription subscription = lockOwned(studentId, subscriptionId);
        if (!subscription.getExpiresOn().isBefore(calendar.today())) {
            throw new SubscriptionException("Срок абонемента ещё не закончился. Чтобы прекратить его раньше, используйте отмену.");
        }
        subscription.expire(adminId, calendar.now());
        return subscriptions.saveAndFlush(subscription);
    }

    /** Daily job: ACTIVE subscriptions whose end date is before {@code today} become EXPIRED. */
    @Transactional
    public int expireOverdue(LocalDate today) {
        List<Subscription> overdue = subscriptions.findByStatusAndExpiresOnBefore(SubscriptionStatus.ACTIVE, today);
        overdue.forEach(s -> s.expire(null, calendar.now()));
        subscriptions.saveAll(overdue);
        return overdue.size();
    }

    private void activateLocked(Subscription subscription, Long adminId) {
        LocalDate today = calendar.today();
        for (Subscription other : subscriptions.findByStudentIdAndStatus(subscription.getStudentId(), SubscriptionStatus.ACTIVE)) {
            if (other.getId().equals(subscription.getId())) {
                continue;
            }
            if (other.getExpiresOn().isBefore(today)) {
                other.expire(null, calendar.now()); // overdue, the daily job just has not run yet
                subscriptions.save(other);
            } else {
                throw new SubscriptionException("У ученика уже есть действующий абонемент (" + other.getPlanName()
                        + " до " + other.getExpiresOn().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        + "). Новый можно активировать после его окончания или отмены.");
            }
        }
        subscription.activate(adminId, calendar.now());
        subscriptions.saveAndFlush(subscription);
        credits.grant(subscription, adminId); // same transaction: if this fails, the activation is rolled back
    }

    private void validatePeriod(LocalDate startsOn, LocalDate expiresOn) {
        if (startsOn == null || expiresOn == null) {
            throw new SubscriptionException("Укажите даты начала и окончания.");
        }
        if (expiresOn.isBefore(startsOn)) {
            throw new SubscriptionException("Дата окончания не может быть раньше даты начала.");
        }
        if (ChronoUnit.DAYS.between(startsOn, expiresOn) >= MAX_DAYS) {
            throw new SubscriptionException("Срок абонемента — не больше года.");
        }
    }

    private void lockStudent(Long studentId) {
        accounts.findByIdForUpdate(studentId)
                .filter(a -> a.getRole() == Role.STUDENT)
                .orElseThrow(() -> new SubscriptionException("Ученик не найден."));
    }

    private Subscription lockOwned(Long studentId, Long subscriptionId) {
        return owned(subscriptions.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new SubscriptionException("Абонемент не найден.")), studentId);
    }

    /** Ownership check: a subscription id is only valid together with its own student id. */
    private static Subscription owned(Subscription subscription, Long studentId) {
        if (!subscription.getStudentId().equals(studentId)) {
            throw new SubscriptionException("Абонемент не найден.");
        }
        return subscription;
    }

    // ----- read models -----

    /** What a student may see about one subscription: no prices or payment records. */
    public record PlanSummary(Long id, String planName, SubscriptionStatus status, LocalDate startsOn, LocalDate expiresOn,
                              int lessonMinutes, int lessonsAllocated, CreditBalance balance) {

        public String statusLabel() {
            return status.label();
        }
    }

    /**
     * Dashboard data for one student, always looked up by the signed-in student's own id.
     *
     * @param current the subscription in force today, if any
     * @param pending a subscription waiting for payment confirmation, if any
     */
    public record StudentPlan(PlanSummary current, PlanSummary pending) {
    }

    @Transactional(readOnly = true)
    public StudentPlan planOf(Long studentId) {
        LocalDate today = calendar.today();
        List<Subscription> all = subscriptions.findByStudentIdOrderByCreatedAtDesc(studentId);
        PlanSummary current = all.stream().filter(s -> s.isUsableOn(today)).findFirst().map(s -> summary(s, today)).orElse(null);
        PlanSummary pending = all.stream().filter(s -> s.getStatus() == SubscriptionStatus.PENDING_PAYMENT)
                .findFirst().map(s -> summary(s, today)).orElse(null);
        return new StudentPlan(current, pending);
    }

    /** Status line for each student in the admin list: the subscription in force, otherwise the latest open one. */
    @Transactional(readOnly = true)
    public Map<Long, PlanSummary> overviewByStudent() {
        LocalDate today = calendar.today();
        Map<Long, PlanSummary> result = new HashMap<>();
        subscriptions.findByStatusIn(EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PENDING_PAYMENT)).stream()
                .sorted(Comparator.comparing((Subscription s) -> s.isUsableOn(today)).thenComparing(Subscription::getCreatedAt))
                .forEach(s -> result.put(s.getStudentId(), summary(s, today))); // usable ones are put last and win
        return result;
    }

    private PlanSummary summary(Subscription s, LocalDate today) {
        return new PlanSummary(s.getId(), s.getPlanName(), s.effectiveStatus(today), s.getStartsOn(), s.getExpiresOn(),
                s.getLessonMinutes(), s.getLessonsIncluded(), credits.balanceOf(s.getId()));
    }

    /** One ledger entry for the history view. {@code restorable}: a consumption that has not been restored yet. */
    public record LedgerLine(Long id, CreditType type, int delta, LocalDate lessonDate, String reason,
                             String actor, String createdAt, boolean restorable) {

        public String typeLabel() {
            return type.label();
        }

        public String deltaText() {
            return delta > 0 ? "+" + delta : "−" + Math.abs(delta);
        }
    }

    /**
     * Administrative view of one subscription — information only; payments and refunds happen outside the site.
     *
     * @param paymentConfirmed when and by whom "Оплата получена" was clicked, or {@code null}
     */
    public record SubscriptionDetails(Subscription subscription, SubscriptionStatus status, CreditBalance balance,
                                      String price, String paymentConfirmed, List<LedgerLine> ledger,
                                      String createdBy, String createdAt, String activatedBy, String closedBy, String closedAt,
                                      boolean canConfirmPayment, boolean canCancel, boolean canMarkExpired, boolean canAdjust) {

        public String statusLabel() {
            return status.label();
        }
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDetails> historyOf(Long studentId) {
        LocalDate today = calendar.today();
        List<Subscription> all = subscriptions.findByStudentIdOrderByCreatedAtDesc(studentId);
        Map<Long, List<CreditTransaction>> entries = all.isEmpty() ? Map.of()
                : credits.entriesOf(all.stream().map(Subscription::getId).toList());
        Map<Long, SubscriptionPayment> confirmations = new HashMap<>();
        Set<Long> actorIds = new HashSet<>();
        for (Subscription s : all) {
            payments.findBySubscriptionIdOrderByPaidOnAscIdAsc(s.getId()).stream().findFirst().ifPresent(p -> {
                confirmations.put(s.getId(), p);
                actorIds.add(p.getRecordedBy());
            });
            actorIds.add(s.getCreatedBy());
            actorIds.add(s.getActivatedBy());
            actorIds.add(s.getClosedBy());
            entries.getOrDefault(s.getId(), List.of()).forEach(e -> actorIds.add(e.getCreatedBy()));
        }
        actorIds.remove(null);
        Map<Long, String> names = accounts.findAllById(actorIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getDisplayName));
        Function<Long, String> name = id -> id == null ? "автоматически" : names.getOrDefault(id, "—");

        return all.stream().map(s -> {
            List<CreditTransaction> ledger = entries.getOrDefault(s.getId(), List.of());
            Set<Long> restored = ledger.stream().map(CreditTransaction::getReversesTransactionId)
                    .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            SubscriptionPayment confirmation = confirmations.get(s.getId());
            return new SubscriptionDetails(
                    s, s.effectiveStatus(today), CreditBalance.of(ledger), Money.format(s.getPriceMinor()),
                    confirmation == null ? null : dateTime(confirmation.getRecordedAt()) + " · " + name.apply(confirmation.getRecordedBy()),
                    ledger.stream().map(e -> new LedgerLine(e.getId(), e.getType(), e.getDelta(), e.getLessonDate(), e.getReason(),
                            name.apply(e.getCreatedBy()), dateTime(e.getCreatedAt()),
                            e.getType() == CreditType.CONSUME && !restored.contains(e.getId()))).toList(),
                    name.apply(s.getCreatedBy()), dateTime(s.getCreatedAt()),
                    s.getActivatedBy() == null ? null : name.apply(s.getActivatedBy()),
                    s.getClosedAt() == null ? null : name.apply(s.getClosedBy()), dateTime(s.getClosedAt()),
                    !s.isComplimentary() && s.getStatus() == SubscriptionStatus.PENDING_PAYMENT && !s.getExpiresOn().isBefore(today),
                    s.getStatus() == SubscriptionStatus.PENDING_PAYMENT || s.getStatus() == SubscriptionStatus.ACTIVE,
                    s.getStatus() == SubscriptionStatus.ACTIVE && s.getExpiresOn().isBefore(today),
                    s.isUsableOn(today));
        }).toList();
    }

    private String dateTime(Instant instant) {
        return instant == null ? null : DATE_TIME.format(instant.atZone(calendar.zone()));
    }
}
