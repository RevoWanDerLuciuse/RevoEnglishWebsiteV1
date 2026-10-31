package org.example.revoenglishwebsite.subscription;

/** Kind of lesson-credit ledger entry. */
public enum CreditType {
    /** Lessons included in the subscription, granted once when it becomes active (+N). */
    GRANT("Начисление по абонементу"),
    /** One lesson used (−1). */
    CONSUME("Занятие списано"),
    /** A consumed lesson given back, e.g. after an eligible cancellation (+1, references the CONSUME). */
    RESTORE("Занятие возвращено"),
    /** Manual correction by an administrator, with a reason (±N). */
    ADJUSTMENT("Ручная корректировка");

    private final String label;

    CreditType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
