package dev.wuoj.common.web;

/** An upload over a size or count limit; answered with 413. The message names the limit. */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String detail) {
        super(detail);
    }
}
