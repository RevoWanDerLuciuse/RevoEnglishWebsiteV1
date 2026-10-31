package org.example.revoenglishwebsite.subscription;

import org.example.revoenglishwebsite.account.AccountService;
import org.example.revoenglishwebsite.account.UserAccountRepository;
import org.example.revoenglishwebsite.pricing.PricingCatalog;
import org.example.revoenglishwebsite.pricing.PricingPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "site.telegram-username=test_teacher")
class SubscriptionServiceTests {

    @Autowired
    private SubscriptionService subscriptions;
    @Autowired
    private LessonCreditService credits;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private CreditTransactionRepository ledger;
    @Autowired
    private SubscriptionPaymentRepository payments;
    @Autowired
    private AccountService accounts;
    @Autowired
    private UserAccountRepository accountRepository;
    @Autowired
    private BusinessCalendar calendar;
    @Autowired
    private JdbcTemplate jdbc;

    private Long admin;
    private Long student;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        String adminLogin = unique("admin");
        accounts.bootstrapAdmin("Админ", adminLogin, "Admin-Pass-0001");
        admin = accountRepository.findByLogin(adminLogin).orElseThrow().getId();
        student = accounts.createStudent("Ученик", unique("student"), "Student-Pass-01").account().getId();
        today = calendar.today();
    }

    // ----- plans, pricing, snapshot -----

    @Test
    void eachPlanAllocatesItsLessonsAndPriceInKopecksAndRoubles() {
        record Expected(String code, int lessons, long priceMinor) {
        }
        for (Expected e : new Expected[]{new Expected("start", 4, 600_000), new Expected("regular", 8, 1_120_000),
                new Expected("intensive", 12, 1_560_000)}) {
            Long s = accounts.createStudent("П " + e.code(), unique(e.code()), "Student-Pass-01").account().getId();
            Subscription sub = active(s, e.code());

            assertThat(sub.getLessonsIncluded()).isEqualTo(e.lessons());
            assertThat(sub.getPriceMinor()).isEqualTo(e.priceMinor());
            assertThat(sub.getCurrency()).isEqualTo("RUB");
            assertThat(sub.getLessonMinutes()).isEqualTo(60);
            assertThat(credits.balanceOf(sub.getId())).isEqualTo(new CreditBalance(e.lessons(), 0, 0, e.lessons()));
            // stored as integers in the database, not floating point
            assertThat(jdbc.queryForObject("select price_minor from subscription where id = ?", Long.class, sub.getId()))
                    .isEqualTo(e.priceMinor());
        }
        assertThat(PricingCatalog.PLANS).extracting(PricingPlan::monthlyPriceMinor).containsExactly(600_000L, 1_120_000L, 1_560_000L);
    }

    @Test
    void existingSubscriptionsKeepTheirPriceWhenThePlanChanges() {
        Subscription original = assignPaid(student, "regular");
        PricingPlan repriced = new PricingPlan("regular", "Regular", 8, 1_350, "", true); // a later price change
        Subscription later = subscriptionRepository.saveAndFlush(Subscription.fromPlan(student, repriced, today,
                today.plusDays(30), null, admin, calendar.now(), "assign:" + UUID.randomUUID()));

        assertThat(subscriptionRepository.findById(original.getId()).orElseThrow().getPriceMinor()).isEqualTo(1_120_000);
        assertThat(later.getPriceMinor()).isEqualTo(1_080_000);
    }

    // ----- activation -----

    @Test
    void paidSubscriptionWaitsUntilTheTeacherConfirmsThePayment() {
        Subscription sub = assignPaid(student, "start");

        assertThat(sub.getStatus()).as("selecting a plan does not activate it").isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        assertThat(credits.balanceOf(sub.getId())).isEqualTo(CreditBalance.EMPTY);

        Outcome<Subscription> confirmed = subscriptions.confirmPayment(student, sub.getId(), admin);
        assertThat(confirmed.created()).isTrue();
        assertThat(confirmed.value().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(confirmed.value().getActivatedBy()).isEqualTo(admin);
        assertThat(credits.balanceOf(sub.getId()).remaining()).isEqualTo(4);
        // the confirmation is kept for the history: plan price, today, who clicked
        var record = payments.findBySubscriptionIdOrderByPaidOnAscIdAsc(sub.getId());
        assertThat(record).singleElement().satisfies(p -> {
            assertThat(p.getAmountMinor()).isEqualTo(600_000);
            assertThat(p.getCurrency()).isEqualTo("RUB");
            assertThat(p.getPaidOn()).isEqualTo(today);
            assertThat(p.getRecordedBy()).isEqualTo(admin);
        });

        // a second click (double click, resubmission) changes nothing
        assertThat(subscriptions.confirmPayment(student, sub.getId(), admin).created()).isFalse();
        assertThat(ledger.findBySubscriptionIdOrderByCreatedAtAscIdAsc(sub.getId())).hasSize(1); // granted once
        assertThat(payments.findBySubscriptionIdOrderByPaidOnAscIdAsc(sub.getId())).hasSize(1);
    }

    @Test
    void complimentarySubscriptionNeedsAReasonIsFreeAndActiveAtOnce() {
        assertThatThrownBy(() -> subscriptions.assign(student, "start", today, today.plusDays(30), " ", admin, key()))
                .isInstanceOf(SubscriptionException.class);

        Subscription sub = subscriptions.assign(student, "start", today, today.plusDays(30), "Пробный месяц", admin, key()).value();

        assertThat(sub.isComplimentary()).isTrue();
        assertThat(sub.getPriceMinor()).isZero();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getCreatedBy()).isEqualTo(admin);
        assertThat(credits.balanceOf(sub.getId()).remaining()).isEqualTo(4);
        assertThatThrownBy(() -> subscriptions.confirmPayment(student, sub.getId(), admin))
                .isInstanceOf(SubscriptionException.class);
    }

    @Test
    void onlyOneSubscriptionCanBeInForceAtATime() {
        active(student, "start");
        Subscription next = assignPaid(student, "regular");

        assertThatThrownBy(() -> subscriptions.confirmPayment(student, next.getId(), admin))
                .isInstanceOf(SubscriptionException.class).hasMessageContaining("действующий");
        assertThat(credits.balanceOf(next.getId())).isEqualTo(CreditBalance.EMPTY);
        assertThat(payments.findBySubscriptionIdOrderByPaidOnAscIdAsc(next.getId())).as("rolled back together").isEmpty();
        assertThat(subscriptionRepository.findById(next.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
    }

    // ----- expiration -----

    @Test
    void subscriptionsExpireAfterTheirEndDate() {
        Subscription sub = subscriptions.assign(student, "start", today.minusDays(5), today, "Тест", admin, key()).value();
        assertThat(sub.isUsableOn(today)).isTrue();
        assertThat(sub.effectiveStatus(today.plusDays(1))).isEqualTo(SubscriptionStatus.EXPIRED);

        assertThatThrownBy(() -> subscriptions.markExpired(student, sub.getId(), admin))
                .isInstanceOf(SubscriptionException.class).hasMessageContaining("ещё не закончился");
        assertThatThrownBy(() -> credits.consumeLesson(student, "lesson-x-" + sub.getId(), today.plusDays(1), null, admin))
                .isInstanceOf(SubscriptionException.class);

        assertThat(subscriptions.expireOverdue(today.plusDays(1))).isGreaterThanOrEqualTo(1);
        Subscription expired = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(expired.getClosedBy()).isNull();
        assertThat(credits.balanceOf(sub.getId()).remaining()).as("history is kept").isEqualTo(4);
    }

    @Test
    void anOverduePreviousSubscriptionIsExpiredWhenTheNextOneIsActivated() {
        Subscription old = active(student, "start");
        jdbc.update("update subscription set starts_on = ?, expires_on = ? where id = ?",
                today.minusDays(40), today.minusDays(10), old.getId());
        Subscription next = assignPaid(student, "regular");

        subscriptions.confirmPayment(student, next.getId(), admin);

        assertThat(subscriptionRepository.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscriptions.planOf(student).current().id()).isEqualTo(next.getId());
    }

    @Test
    void cancellationIsAMarkerWithAnOptionalCommentAndStopsConsumption() {
        Subscription sub = active(student, "start");

        subscriptions.cancel(student, sub.getId(), "", admin);
        assertThat(subscriptionRepository.findById(sub.getId()).orElseThrow().getCloseReason()).isNull();

        assertThat(subscriptionRepository.findById(sub.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThatThrownBy(() -> credits.consumeLesson(student, "lesson-c-" + sub.getId(), today, null, admin))
                .isInstanceOf(SubscriptionException.class);
    }

    // ----- ledger -----

    @Test
    void consumingAndRestoringLessonsUpdatesTheDerivedBalance() {
        Subscription sub = active(student, "regular");
        String ref = "lesson-" + UUID.randomUUID();

        CreditTransaction used = credits.consumeLesson(student, ref, today, "урок 1", admin).value();
        assertThat(credits.balanceOf(sub.getId())).isEqualTo(new CreditBalance(8, 0, 1, 7));

        credits.restoreLesson(student, used.getId(), "", admin); // comment is optional
        assertThat(credits.balanceOf(sub.getId())).isEqualTo(new CreditBalance(8, 0, 0, 8));

        assertThat(ledger.findBySubscriptionIdOrderByCreatedAtAscIdAsc(sub.getId()))
                .extracting(CreditTransaction::getType).containsExactly(CreditType.GRANT, CreditType.CONSUME, CreditType.RESTORE);
    }

    @Test
    void duplicateSubmissionsAreRecordedOnlyOnce() {
        Subscription sub = active(student, "regular");
        String ref = "lesson-" + UUID.randomUUID();

        assertThat(credits.consumeLesson(student, ref, today, null, admin).created()).isTrue();
        assertThat(credits.consumeLesson(student, ref, today, null, admin).created()).isFalse();

        CreditTransaction used = ledger.findByRequestKey("consume:" + ref).orElseThrow();
        assertThat(credits.restoreLesson(student, used.getId(), "Перенос", admin).created()).isTrue();
        assertThat(credits.restoreLesson(student, used.getId(), "Перенос", admin).created()).isFalse();

        String adjustKey = key();
        assertThat(credits.adjust(student, sub.getId(), 2, "Подарок", admin, adjustKey).created()).isTrue();
        assertThat(credits.adjust(student, sub.getId(), 2, "Подарок", admin, adjustKey).created()).isFalse();

        String assignKey = key();
        Subscription first = subscriptions.assign(student, "start", today, today.plusDays(30), null, admin, assignKey).value();
        assertThat(subscriptions.assign(student, "start", today, today.plusDays(30), null, admin, assignKey).value().getId())
                .isEqualTo(first.getId());


        assertThat(credits.balanceOf(sub.getId())).isEqualTo(new CreditBalance(8, 2, 0, 10));
        // and the database itself refuses a second entry with the same key
        assertThatThrownBy(() -> jdbc.update("insert into lesson_credit_transaction (subscription_id, type, delta, created_at, request_key) "
                + "values (?, 'GRANT', 8, current_timestamp, ?)", sub.getId(), "grant:" + sub.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void simultaneousDoubleSubmitsAreRecordedOnlyOnce() throws Exception {
        Subscription sub = active(student, "regular");
        String assignKey = key();
        String ref = "lesson-" + UUID.randomUUID();

        List<Outcome<?>> assigned = twiceAtOnce(() -> subscriptions.assign(student, "start", today, today.plusDays(30), null, admin, assignKey));
        List<Outcome<?>> consumed = twiceAtOnce(() -> credits.consumeLesson(student, ref, today, null, admin));

        assertThat(assigned).extracting(Outcome::created).containsExactlyInAnyOrder(true, false);
        assertThat(consumed).extracting(Outcome::created).containsExactlyInAnyOrder(true, false);
        assertThat(credits.balanceOf(sub.getId())).isEqualTo(new CreditBalance(8, 0, 1, 7));
    }

    @Test
    void balanceNeverGoesNegative() {
        Subscription sub = active(student, "start");
        for (int i = 0; i < 4; i++) {
            credits.consumeLesson(student, "lesson-" + UUID.randomUUID(), today, null, admin);
        }
        assertThatThrownBy(() -> credits.consumeLesson(student, "lesson-" + UUID.randomUUID(), today, null, admin))
                .isInstanceOf(SubscriptionException.class).hasMessageContaining("не осталось");
        assertThatThrownBy(() -> credits.adjust(student, sub.getId(), -1, "Ошибка", admin, key()))
                .isInstanceOf(SubscriptionException.class).hasMessageContaining("отрицательным");
        assertThat(credits.balanceOf(sub.getId())).isEqualTo(new CreditBalance(4, 0, 4, 0));
    }

    @Test
    void invalidAdjustmentsAreRejected() {
        Subscription sub = active(student, "start");
        Subscription pending = assignPaid(student, "regular");

        assertThatThrownBy(() -> credits.adjust(student, sub.getId(), 0, "Ноль", admin, key())).isInstanceOf(SubscriptionException.class);
        assertThatThrownBy(() -> credits.adjust(student, sub.getId(), 51, "Много", admin, key())).isInstanceOf(SubscriptionException.class);
        assertThatThrownBy(() -> credits.adjust(student, sub.getId(), 1, "", admin, key())).isInstanceOf(SubscriptionException.class);
        assertThatThrownBy(() -> credits.adjust(student, pending.getId(), 1, "Не активен", admin, key())).isInstanceOf(SubscriptionException.class);
        assertThatThrownBy(() -> credits.adjust(student, sub.getId(), 1, "Без ключа", admin, "not-a-uuid")).isInstanceOf(SubscriptionException.class);

        credits.adjust(student, sub.getId(), -1, "Ошибочно начислено", admin, key());
        assertThat(credits.balanceOf(sub.getId())).isEqualTo(new CreditBalance(4, -1, 0, 3));
    }

    @Test
    void onlyAdministratorsMayChangeSubscriptionsOrCredits() {
        Subscription sub = active(student, "start");

        assertThatThrownBy(() -> credits.adjust(student, sub.getId(), 5, "Сам себе", student, key())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> subscriptions.assign(student, "intensive", today, today.plusDays(30), "Сам себе", student, key()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> credits.consumeLesson(student, "lesson-" + UUID.randomUUID(), today, null, student))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> subscriptions.cancel(student, sub.getId(), "Сам себе", null)).isInstanceOf(AccessDeniedException.class);
        assertThat(credits.balanceOf(sub.getId()).remaining()).isEqualTo(4);
    }

    @Test
    void operationsCheckThatTheSubscriptionBelongsToTheStudent() {
        Subscription sub = active(student, "start");
        Long other = accounts.createStudent("Другой", unique("other"), "Student-Pass-01").account().getId();

        assertThatThrownBy(() -> credits.adjust(other, sub.getId(), 1, "Чужой", admin, key())).isInstanceOf(SubscriptionException.class);
        assertThatThrownBy(() -> subscriptions.cancel(other, sub.getId(), "Чужой", admin)).isInstanceOf(SubscriptionException.class);
        CreditTransaction used = credits.consumeLesson(student, "lesson-" + UUID.randomUUID(), today, null, admin).value();
        assertThatThrownBy(() -> credits.restoreLesson(other, used.getId(), "Чужой", admin)).isInstanceOf(SubscriptionException.class);
        assertThat(subscriptions.planOf(other).current()).isNull();
    }

    @Test
    void aFailedActivationLeavesNothingBehind() {
        Subscription sub = assignPaid(student, "regular");
        // Simulate a failure in the second half of the confirmation: the grant key is already taken
        jdbc.update("insert into lesson_credit_transaction (subscription_id, type, delta, created_at, request_key) "
                + "values (?, 'GRANT', 1, current_timestamp, ?)", sub.getId(), "grant:" + sub.getId());

        assertThatThrownBy(() -> subscriptions.confirmPayment(student, sub.getId(), admin)).isInstanceOf(DataIntegrityViolationException.class);

        Subscription after = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(after.getStatus()).as("status change rolled back").isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        assertThat(after.getActivatedAt()).isNull();
        assertThat(payments.findBySubscriptionIdOrderByPaidOnAscIdAsc(sub.getId())).as("payment confirmation rolled back").isEmpty();
        assertThat(ledger.findBySubscriptionIdOrderByCreatedAtAscIdAsc(sub.getId())).hasSize(1);
    }

    @Test
    void moneyIsFormattedFromKopecks() {
        assertThat(Money.format(1_120_000)).isEqualTo("11\u00A0200\u00A0₽");
        assertThat(Money.format(1_120_050)).isEqualTo("11\u00A0200,50\u00A0₽");
    }

    // ----- helpers -----

    private Subscription assignPaid(Long studentId, String plan) {
        return subscriptions.assign(studentId, plan, today, today.plusMonths(1).minusDays(1), null, admin, key()).value();
    }

    private Subscription active(Long studentId, String plan) {
        Subscription sub = assignPaid(studentId, plan);
        return subscriptions.confirmPayment(studentId, sub.getId(), admin).value();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    /** Sends the same request from two threads at the same moment, like a double click on a form button. */
    private static List<Outcome<?>> twiceAtOnce(Callable<Outcome<?>> request) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Outcome<?>> together = () -> {
            start.await();
            return request.call();
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Outcome<?>>> results = List.of(pool.submit(together), pool.submit(together));
            List<Outcome<?>> outcomes = new ArrayList<>();
            for (Future<Outcome<?>> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        }
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
