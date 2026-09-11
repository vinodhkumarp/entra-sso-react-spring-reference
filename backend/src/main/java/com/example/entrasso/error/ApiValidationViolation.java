package com.example.entrasso.error;

/**
 * Describes one invalid request value without returning the rejected value itself.
 *
 * @param field request field, parameter, or validation path
 * @param message client-safe validation explanation
 */
public record ApiValidationViolation(String field, String message) {}
