package com.abhiknow.provider;

public class ProviderException extends Exception {
    public enum Kind {
        TIMEOUT(true), UNAVAILABLE(true), RATE_LIMITED(false), HTTP_ERROR(false), NOT_FOUND(false), PARSE_ERROR(false);
        public final boolean retryable;
        Kind(boolean retryable) { this.retryable = retryable; }
    }

    private final Kind kind;

    public ProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
