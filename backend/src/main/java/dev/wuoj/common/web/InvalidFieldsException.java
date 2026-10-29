package dev.wuoj.common.web;

import java.util.List;
import java.util.stream.Collectors;

/** Input that passed the request's shape checks but is still invalid; answered with 400 and the field errors. */
public class InvalidFieldsException extends RuntimeException {

    private final List<FieldErrorDto> errors;

    public InvalidFieldsException(String field, String message) {
        this(List.of(new FieldErrorDto(field, message)));
    }

    /** At least one error. */
    public InvalidFieldsException(List<FieldErrorDto> errors) {
        super(errors.stream().map(e -> e.field() + " " + e.message()).collect(Collectors.joining("; ")));
        if (errors.isEmpty()) throw new IllegalArgumentException("No field errors");
        this.errors = List.copyOf(errors);
    }

    public List<FieldErrorDto> errors() {
        return errors;
    }
}
