package org.example.revoenglishwebsite.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;

/** A payment recorded manually by the teacher. Never changed after it is written. Not shown to students. */
@Entity
@Immutable
@Table(name = "subscription_payment")
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(length = 500)
    private String note;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;

    protected SubscriptionPayment() {
        // for JPA
    }

    SubscriptionPayment(Long subscriptionId, long amountMinor, LocalDate paidOn, String note,
                        Long recordedBy, Instant recordedAt, String requestKey) {
        this.subscriptionId = subscriptionId;
        this.amountMinor = amountMinor;
        this.currency = Money.RUB;
        this.paidOn = paidOn;
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
        this.requestKey = requestKey;
    }

    public Long getId() {
        return id;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public String getNote() {
        return note;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Long getRecordedBy() {
        return recordedBy;
    }
}
