package com.urlshortener.exception;

/**
 * Thrown when a caller-supplied custom alias is already in use.
 * Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 */
public class AliasAlreadyTakenException extends RuntimeException {
    private final String alias;

    public AliasAlreadyTakenException(String alias) {
        super("Custom alias '" + alias + "' is already in use");
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }
}
