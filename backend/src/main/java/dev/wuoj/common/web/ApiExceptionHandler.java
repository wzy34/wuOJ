package dev.wuoj.common.web;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns every error into an RFC 9457 problem; invalid input additionally lists the offending fields. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String INVALID = "The request is invalid.";

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemResponses.problem(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
                .body(ProblemResponses.problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(InvalidFieldsException.class)
    ResponseEntity<ProblemDetail> invalidFields(InvalidFieldsException ex) {
        ProblemDetail body = ProblemResponses.problem(HttpStatus.BAD_REQUEST, INVALID);
        body.setProperty("errors", ex.errors());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemResponses.problem(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ProblemResponses.problem(HttpStatus.FORBIDDEN, ex.getMessage()));
    }

    /** Security exceptions are left to Spring Security's handlers; anything else is a bug. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) throw ex;
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(ProblemResponses.problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred."));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDto> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.add(new FieldErrorDto(e.getField(), message(e.getDefaultMessage()))));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.add(new FieldErrorDto(e.getObjectName(), message(e.getDefaultMessage()))));
        return invalid(ex, errors, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDto> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> errors
                .add(new FieldErrorDto(parameterName(result.getMethodParameter()),
                        message(error.getDefaultMessage())))));
        return invalid(ex, errors, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String field = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : String.valueOf(ex.getPropertyName());
        return invalid(ex, List.of(new FieldErrorDto(field, "has an invalid value")), headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return invalid(ex, List.of(new FieldErrorDto(ex.getParameterName(), "is required")), headers, status,
                request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemResponses.problem(HttpStatus.BAD_REQUEST, "The request body is not valid JSON.");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private @Nullable ResponseEntity<Object> invalid(Exception ex, List<FieldErrorDto> errors, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemResponses.problem(HttpStatus.BAD_REQUEST, INVALID);
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** The name clients use: the {@code @RequestParam} name when given, else the Java parameter name. */
    private static String parameterName(MethodParameter parameter) {
        RequestParam annotation = parameter.getParameterAnnotation(RequestParam.class);
        if (annotation != null && !annotation.name().isEmpty()) return annotation.name();
        if (annotation != null && !annotation.value().isEmpty()) return annotation.value();
        return String.valueOf(parameter.getParameterName());
    }

    private static String message(@Nullable String message) {
        return message == null ? "is invalid" : message;
    }
}
