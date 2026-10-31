package org.example.revoenglishwebsite.subscription;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The lesson-credit ledger. Every change is a new {@link CreditTransaction}; the balance is the sum of the
 * subscription's entries and is checked under a row lock on the subscription before anything is written.
 * <p>
 * {@link #consumeLesson} and {@link #restoreLesson} are the entry points for the future calendar: it will pass
 * its own lesson reference (e.g. "lesson:42") — the reference is unique, so a lesson can never be charged twice.
 */
@Service
public class LessonCreditService {

    static final int MAX_ADJUSTMENT = 50;

    private final SubscriptionRepository subscriptions;
    private final CreditTransactionRepository ledger;
    private final AdminGuard guard;
    private final BusinessCalendar calendar;

    public LessonCreditService(SubscriptionRepository subscriptions, CreditTransactionRepository ledger,
                               AdminGuard guard, BusinessCalendar calendar) {
        this.subscriptions = subscriptions;
        this.ledger = ledger;
        this.guard = guard;
        this.calendar = calendar;
    }

    /**
     * Uses one lesson from the student's subscription that is active on {@code lessonDate}.
     *
     * @param lessonRef unique reference of the lesson; the same reference is charged only once
     */
    @Transactional
    public Outcome<CreditTransaction> consumeLesson(Long studentId, String lessonRef, LocalDate lessonDate, String note, Long actorId) {
        guard.requireAdmin(actorId);
        guard.requireStudent(studentId);
        String ref = Inputs.lessonRef(lessonRef);
        if (lessonDate == null) {
            throw new SubscriptionException("Укажите дату занятия.");
        }
        String key = CreditTransaction.consumeKey(ref);
        Optional<CreditTransaction> existing = ledger.findByRequestKey(key);
        if (existing.isPresent()) {
            return new Outcome<>(ownedBy(existing.get(), studentId), false);
        }
        Subscription candidate = subscriptions.findByStudentIdAndStatus(studentId, SubscriptionStatus.ACTIVE).stream()
                .filter(s -> s.isUsableOn(lessonDate))
                .findFirst()
                .orElseThrow(() -> new SubscriptionException("На эту дату у ученика нет активного абонемента."));
        Subscription subscription = lock(candidate.getId());
        // Checked again under the lock: a double-submitted form waits for the lock until the first request has
        // committed, and must then find that consumption instead of failing on the unique key.
        existing = ledger.findByRequestKey(key);
        if (existing.isPresent()) {
            return new Outcome<>(ownedBy(existing.get(), studentId), false);
        }
        if (!subscription.isUsableOn(lessonDate)) {
            throw new SubscriptionException("На эту дату у ученика нет активного абонемента.");
        }
        if (balanceOf(subscription.getId()).remaining() < 1) {
            throw new SubscriptionException("В абонементе не осталось занятий.");
        }
        CreditTransaction entry = CreditTransaction.consume(subscription.getId(), ref, lessonDate, Inputs.optionalText(note), actorId, calendar.now());
        return new Outcome<>(ledger.saveAndFlush(entry), true);
    }

    /** Gives back a consumed lesson (e.g. after a cancellation the teacher accepted). Each consumption can be restored once. */
    @Transactional
    public Outcome<CreditTransaction> restoreLesson(Long studentId, Long consumptionId, String reason, Long actorId) {
        guard.requireAdmin(actorId);
        String note = Inputs.optionalText(reason);
        CreditTransaction consumption = ledger.findById(consumptionId)
                .filter(t -> t.getType() == CreditType.CONSUME)
                .orElseThrow(() -> new SubscriptionException("Списание не найдено."));
        ownedBy(consumption, studentId);
        lock(consumption.getSubscriptionId());
        Optional<CreditTransaction> existing = ledger.findByRequestKey("restore:" + consumption.getId());
        if (existing.isPresent()) {
            return new Outcome<>(existing.get(), false);
        }
        return new Outcome<>(ledger.saveAndFlush(CreditTransaction.restore(consumption, note, actorId, calendar.now())), true);
    }

    /**
     * Manual correction (positive: extra lessons granted; negative: correction) with a mandatory reason.
     * Only on a subscription that is active today, and never below a zero balance.
     */
    @Transactional
    public Outcome<CreditTransaction> adjust(Long studentId, Long subscriptionId, int delta, String reason, Long adminId, String formKey) {
        guard.requireAdmin(adminId);
        String key = Inputs.requestKey("adjust", formKey);
        if (delta == 0 || Math.abs(delta) > MAX_ADJUSTMENT) {
            throw new SubscriptionException("Корректировка: целое число от −50 до 50, не ноль.");
        }
        String why = Inputs.reason(reason, "причину корректировки");
        Subscription subscription = lockOwned(studentId, subscriptionId);
        Optional<CreditTransaction> existing = ledger.findByRequestKey(key);
        if (existing.isPresent()) {
            return new Outcome<>(existing.get(), false);
        }
        if (!subscription.isUsableOn(calendar.today())) {
            throw new SubscriptionException("Корректировать можно только действующий активный абонемент.");
        }
        if (balanceOf(subscription.getId()).remaining() + delta < 0) {
            throw new SubscriptionException("Баланс не может стать отрицательным.");
        }
        CreditTransaction entry = CreditTransaction.adjustment(subscription.getId(), delta, why, adminId, calendar.now(), key);
        return new Outcome<>(ledger.saveAndFlush(entry), true);
    }

    @Transactional(readOnly = true)
    public CreditBalance balanceOf(Long subscriptionId) {
        return CreditBalance.of(ledger.findBySubscriptionIdOrderByCreatedAtAscIdAsc(subscriptionId));
    }

    @Transactional(readOnly = true)
    public List<CreditTransaction> entries(Long subscriptionId) {
        return ledger.findBySubscriptionIdOrderByCreatedAtAscIdAsc(subscriptionId);
    }

    /** Ledger entries of several subscriptions, grouped by subscription id, in creation order. */
    @Transactional(readOnly = true)
    public Map<Long, List<CreditTransaction>> entriesOf(Collection<Long> subscriptionIds) {
        return ledger.findBySubscriptionIdIn(subscriptionIds).stream()
                .sorted(Comparator.comparing(CreditTransaction::getCreatedAt).thenComparing(CreditTransaction::getId))
                .collect(Collectors.groupingBy(CreditTransaction::getSubscriptionId, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Grants the plan's lessons when a subscription becomes active. Called inside the activation transaction;
     * the unique key "grant:&lt;id&gt;" makes a second grant impossible — it fails and rolls back the activation.
     */
    void grant(Subscription subscription, Long adminId) {
        ledger.saveAndFlush(CreditTransaction.grant(subscription, adminId, calendar.now()));
    }

    private Subscription lock(Long subscriptionId) {
        return subscriptions.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new SubscriptionException("Абонемент не найден."));
    }

    private Subscription lockOwned(Long studentId, Long subscriptionId) {
        Subscription subscription = lock(subscriptionId);
        if (!subscription.getStudentId().equals(studentId)) {
            throw new SubscriptionException("Абонемент не найден.");
        }
        return subscription;
    }

    private CreditTransaction ownedBy(CreditTransaction entry, Long studentId) {
        Subscription subscription = subscriptions.findById(entry.getSubscriptionId()).orElseThrow();
        if (!subscription.getStudentId().equals(studentId)) {
            throw new SubscriptionException("Списание не найдено.");
        }
        return entry;
    }
}
