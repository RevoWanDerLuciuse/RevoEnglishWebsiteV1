package org.example.revoenglishwebsite.subscription;

/**
 * Life cycle: PENDING_PAYMENT → ACTIVE (explicit administrator action) → EXPIRED (after expires_on) or CANCELLED.
 * A complimentary subscription is created directly as ACTIVE.
 */
public enum SubscriptionStatus {
    PENDING_PAYMENT("Ожидает оплаты"),
    ACTIVE("Активен"),
    EXPIRED("Истёк"),
    CANCELLED("Отменён");

    private final String label;

    SubscriptionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
