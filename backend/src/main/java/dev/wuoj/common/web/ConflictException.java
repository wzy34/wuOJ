package dev.wuoj.common.web;

/** The change is not allowed in the resource's current state; answered with 409. The message says why. */
public class ConflictException extends RuntimeException {

    public ConflictException(String detail) {
        super(detail);
    }
}
