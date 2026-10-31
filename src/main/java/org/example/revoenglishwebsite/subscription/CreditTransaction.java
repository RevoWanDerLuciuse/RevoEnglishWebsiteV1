package org.example.revoenglishwebsite.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One entry of the append-only lesson-credit ledger. Entries are never updated or deleted; a mistake is
 * corrected by a new entry (RESTORE or ADJUSTMENT). Created only by {@link LessonCreditService}.
 */
@Entity
@Immutable
@Table(name = "lesson_credit_transaction")
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CreditType type;

    @Column(nullable = false)
    private int delta;

    @Column(name = "lesson_ref", length = 100)
    private String lessonRef;

    @Column(name = "lesson_date")
    private LocalDate lessonDate;

    @Column(name = "reverses_transaction_id")
    private Long reversesTransactionId;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "request_key", nullable = false, length = 150)
    private String requestKey;

    protected CreditTransaction() {
        // for JPA
    }

    private CreditTransaction(Long subscriptionId, CreditType type, int delta, Long createdBy, Instant createdAt, String requestKey) {
        this.subscriptionId = subscriptionId;
        this.type = type;
        this.delta = delta;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.requestKey = requestKey;
    }

    static CreditTransaction grant(Subscription subscription, Long adminId, Instant now) {
        CreditTransaction t = new CreditTransaction(subscription.getId(), CreditType.GRANT, subscription.getLessonsIncluded(),
                adminId, now, grantKey(subscription.getId()));
        t.reason = subscription.isComplimentary() ? "Бесплатный абонемент: " + subscription.getComplimentaryReason() : null;
        return t;
    }

    static CreditTransaction consume(Long subscriptionId, String lessonRef, LocalDate lessonDate, String note, Long actorId, Instant now) {
        CreditTransaction t = new CreditTransaction(subscriptionId, CreditType.CONSUME, -1, actorId, now, consumeKey(lessonRef));
        t.lessonRef = lessonRef;
        t.lessonDate = lessonDate;
        t.reason = note;
        return t;
    }

    static CreditTransaction restore(CreditTransaction consumption, String reason, Long actorId, Instant now) {
        CreditTransaction t = new CreditTransaction(consumption.subscriptionId, CreditType.RESTORE, 1, actorId, now,
                "restore:" + consumption.id);
        t.reversesTransactionId = consumption.id;
        t.lessonRef = consumption.lessonRef;
        t.lessonDate = consumption.lessonDate;
        t.reason = reason;
        return t;
    }

    static CreditTransaction adjustment(Long subscriptionId, int delta, String reason, Long adminId, Instant now, String requestKey) {
        CreditTransaction t = new CreditTransaction(subscriptionId, CreditType.ADJUSTMENT, delta, adminId, now, requestKey);
        t.reason = reason;
        return t;
    }

    static String grantKey(Long subscriptionId) {
        return "grant:" + subscriptionId;
    }

    static String consumeKey(String lessonRef) {
        return "consume:" + lessonRef;
    }

    public Long getId() {
        return id;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public CreditType getType() {
        return type;
    }

    public int getDelta() {
        return delta;
    }

    public String getLessonRef() {
        return lessonRef;
    }

    public LocalDate getLessonDate() {
        return lessonDate;
    }

    public Long getReversesTransactionId() {
        return reversesTransactionId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public String getRequestKey() {
        return requestKey;
    }
}
