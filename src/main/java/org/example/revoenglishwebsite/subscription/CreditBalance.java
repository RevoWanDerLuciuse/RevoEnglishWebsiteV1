package org.example.revoenglishwebsite.subscription;

import java.util.Collection;

/**
 * Lesson balance of one subscription, always computed from its ledger entries (never stored).
 *
 * @param allocated lessons granted with the subscription (the plan's allocation)
 * @param adjusted  net manual adjustments (+/−)
 * @param used      lessons consumed minus lessons restored
 * @param remaining sum of all entries = allocated + adjusted − used
 */
public record CreditBalance(int allocated, int adjusted, int used, int remaining) {

    public static final CreditBalance EMPTY = new CreditBalance(0, 0, 0, 0);

    public static CreditBalance of(Collection<CreditTransaction> entries) {
        int allocated = 0;
        int adjusted = 0;
        int used = 0;
        for (CreditTransaction entry : entries) {
            switch (entry.getType()) {
                case GRANT -> allocated += entry.getDelta();
                case ADJUSTMENT -> adjusted += entry.getDelta();
                case CONSUME, RESTORE -> used -= entry.getDelta();
            }
        }
        return new CreditBalance(allocated, adjusted, used, allocated + adjusted - used);
    }

    /** Lessons available in total: the allocation plus manual adjustments. */
    public int totalIncluded() {
        return allocated + adjusted;
    }
}
