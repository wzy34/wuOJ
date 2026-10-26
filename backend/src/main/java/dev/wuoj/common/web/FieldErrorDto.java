package dev.wuoj.common.web;

/** An entry of a problem's {@code errors} list (the contract's {@code FieldError}). */
public record FieldErrorDto(String field, String message) {}
