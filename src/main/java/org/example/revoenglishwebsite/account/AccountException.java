package org.example.revoenglishwebsite.account;

/**
 * A rejected account operation with a user-facing (Russian) message.
 *
 * @see #field() the form field the message belongs to, or {@code null} for a general error
 */
public class AccountException extends RuntimeException {

    private final String field;

    public AccountException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
