package dev.wuoj.common.web;

/** A 1-based page of a list, as in the contract's {@code page} and {@code size} parameters. */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Invalid page " + page + " / size " + size);
        }
    }

    public long offset() {
        return (long) (page - 1) * size;
    }
}
