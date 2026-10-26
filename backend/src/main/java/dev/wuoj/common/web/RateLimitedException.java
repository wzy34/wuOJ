package dev.wuoj.common.web;

/** Answered with 429 and a {@code Retry-After} header. The message is shown to users as is. */
public class RateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds, String detail) {
        super(detail);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
