package dev.wuoj.common.web;

/** The action is not available to anyone right now (e.g. closed registration); answered with 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String detail) {
        super(detail);
    }
}
