package dev.wuoj.common.web;

import java.util.List;

/** Input that passed the request's shape checks but is still invalid; answered with 400 and the field errors. */
public class InvalidFieldsException extends RuntimeException {

    private final List<FieldErrorDto> errors;

    public InvalidFieldsException(String field, String message) {
        super(field + " " + message);
        this.errors = List.of(new FieldErrorDto(field, message));
    }

    public List<FieldErrorDto> errors() {
        return errors;
    }
}
