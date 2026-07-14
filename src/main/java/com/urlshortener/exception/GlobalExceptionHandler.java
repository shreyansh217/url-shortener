package com.urlshortener.exception;

import com.urlshortener.validation.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralised exception → HTTP response mapping using RFC 7807 ProblemDetail.
 *
 * <p>Spring Boot 3.x ships ProblemDetail support natively; we use it here so that
 * error responses have a consistent, machine-readable shape:
 * <pre>
 * {
 *   "type":      "about:blank",
 *   "title":     "Not Found",
 *   "status":    404,
 *   "detail":    "No URL mapping found for code: xyz",
 *   "timestamp": "2025-01-01T00:00:00Z"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UrlNotFoundException.class)
    public ProblemDetail handleNotFound(UrlNotFoundException ex) {
        log.warn("Short code not found: {}", ex.getCode());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Short Code Not Found");
        problem.setProperty("code", ex.getCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AliasAlreadyTakenException.class)
    public ProblemDetail handleAliasConflict(AliasAlreadyTakenException ex) {
        log.warn("Custom alias conflict: {}", ex.getAlias());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Alias Already Taken");
        problem.setProperty("alias", ex.getAlias());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(UrlValidator.InvalidUrlException.class)
    public ProblemDetail handleInvalidUrl(UrlValidator.InvalidUrlException ex) {
        log.warn("Invalid URL submitted: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid URL");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Request validation failed: {}", details);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, details);
        problem.setTitle("Validation Failed");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
