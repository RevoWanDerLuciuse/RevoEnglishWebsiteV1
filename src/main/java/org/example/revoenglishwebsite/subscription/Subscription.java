package org.example.revoenglishwebsite.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.example.revoenglishwebsite.pricing.PricingPlan;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One student's subscription. Holds a snapshot of the plan taken at assignment (name, lessons, price, currency),
 * so later changes to {@link org.example.revoenglishwebsite.pricing.PricingCatalog} do not alter it.
 * The lesson balance is not stored here — it is the sum of the subscription's {@link CreditTransaction}s.
 * State changes go through {@link SubscriptionService}; the transition methods below enforce the life cycle.
 */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Column(name = "plan_code", nullable = false, updatable = false, length = 20)
    private String planCode;

    @Column(name = "plan_name", nullable = false, updatable = false, length = 50)
    private String planName;

    @Column(name = "lessons_included", nullable = false, updatable = false)
    private int lessonsIncluded;

    @Column(name = "lesson_minutes", nullable = false, updatable = false)
    private int lessonMinutes;

    @Column(name = "price_minor", nullable = false, updatable = false)
    private long priceMinor;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private boolean complimentary;

    @Column(name = "complimentary_reason", updatable = false, length = 500)
    private String complimentaryReason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "starts_on", nullable = false, updatable = false)
    private LocalDate startsOn;

    @Column(name = "expires_on", nullable = false, updatable = false)
    private LocalDate expiresOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "activated_by")
    private Long activatedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "close_reason", length = 500)
    private String closeReason;

    @Column(name = "request_key", nullable = false, updatable = false, length = 100)
    private String requestKey;

    @Version
    @Column(nullable = false)
    private long version;

    protected Subscription() {
        // for JPA
    }

    /** New subscription from a plan definition; paid ones wait for payment, complimentary ones are free. */
    static Subscription fromPlan(Long studentId, PricingPlan plan, LocalDate startsOn, LocalDate expiresOn,
                                 String complimentaryReason, Long adminId, Instant now, String requestKey) {
        Subscription s = new Subscription();
        s.studentId = studentId;
        s.planCode = plan.id();
        s.planName = plan.name();
        s.lessonsIncluded = plan.lessonsPerMonth();
        s.lessonMinutes = plan.lessonMinutes();
        s.complimentary = complimentaryReason != null;
        s.complimentaryReason = complimentaryReason;
        s.priceMinor = s.complimentary ? 0 : plan.monthlyPriceMinor();
        s.currency = PricingPlan.CURRENCY;
        s.status = SubscriptionStatus.PENDING_PAYMENT;
        s.startsOn = startsOn;
        s.expiresOn = expiresOn;
        s.createdAt = now;
        s.createdBy = adminId;
        s.requestKey = requestKey;
        return s;
    }

    void activate(Long adminId, Instant now) {
        requireStatus(SubscriptionStatus.PENDING_PAYMENT, "Активировать можно только абонемент, ожидающий оплаты.");
        status = SubscriptionStatus.ACTIVE;
        activatedAt = now;
        activatedBy = adminId;
    }

    void cancel(String reason, Long adminId, Instant now) {
        if (status != SubscriptionStatus.PENDING_PAYMENT && status != SubscriptionStatus.ACTIVE) {
            throw new SubscriptionException("Отменить можно только ожидающий оплаты или активный абонемент.");
        }
        close(SubscriptionStatus.CANCELLED, reason, adminId, now);
    }

    /** @param adminId {@code null} for the automatic daily expiry */
    void expire(Long adminId, Instant now) {
        requireStatus(SubscriptionStatus.ACTIVE, "Отметить истёкшим можно только активный абонемент.");
        close(SubscriptionStatus.EXPIRED, adminId == null ? "Срок действия закончился" : "Отмечен истёкшим вручную", adminId, now);
    }

    private void close(SubscriptionStatus newStatus, String reason, Long adminId, Instant now) {
        status = newStatus;
        closedAt = now;
        closedBy = adminId;
        closeReason = reason;
    }

    private void requireStatus(SubscriptionStatus expected, String message) {
        if (status != expected) {
            throw new SubscriptionException(message);
        }
    }

    /** ACTIVE and not past its expiry date on the given day. */
    public boolean isUsableOn(LocalDate day) {
        return status == SubscriptionStatus.ACTIVE && !day.isBefore(startsOn) && !day.isAfter(expiresOn);
    }

    /** Status as visitors should see it: an ACTIVE subscription past its end date counts as EXPIRED. */
    public SubscriptionStatus effectiveStatus(LocalDate today) {
        return status == SubscriptionStatus.ACTIVE && today.isAfter(expiresOn) ? SubscriptionStatus.EXPIRED : status;
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public int getLessonsIncluded() {
        return lessonsIncluded;
    }

    public int getLessonMinutes() {
        return lessonMinutes;
    }

    public long getPriceMinor() {
        return priceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isComplimentary() {
        return complimentary;
    }

    public String getComplimentaryReason() {
        return complimentaryReason;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Long getActivatedBy() {
        return activatedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Long getClosedBy() {
        return closedBy;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public String getRequestKey() {
        return requestKey;
    }
}
