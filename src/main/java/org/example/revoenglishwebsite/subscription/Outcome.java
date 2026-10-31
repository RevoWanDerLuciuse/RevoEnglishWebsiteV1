package org.example.revoenglishwebsite.subscription;

/**
 * Result of an idempotent operation.
 *
 * @param created {@code false} when the same request had already been recorded and nothing new was written
 */
public record Outcome<T>(T value, boolean created) {
}
