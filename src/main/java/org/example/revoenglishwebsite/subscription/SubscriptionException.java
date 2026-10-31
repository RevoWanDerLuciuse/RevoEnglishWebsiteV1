package org.example.revoenglishwebsite.subscription;

/** A rejected subscription or credit operation, with a user-facing (Russian) message. Rolls back the transaction. */
public class SubscriptionException extends RuntimeException {

    public SubscriptionException(String message) {
        super(message);
    }
}
