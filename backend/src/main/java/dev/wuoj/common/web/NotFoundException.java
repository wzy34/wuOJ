package dev.wuoj.common.web;

/** The resource does not exist, or the caller may not know that it exists; answered with 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String detail) {
        super(detail);
    }
}
