package com.example.ratelimiter.api.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setTitle("Resource Not Found");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed"
        );
        problem.setTitle("Validation Error");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch: {}", ex.getMessage());
        String message = String.format("Invalid value '%s' for parameter '%s'",
                ex.getValue(), ex.getName());
        if (ex.getRequiredType() != null && ex.getRequiredType().equals(java.util.UUID.class)) {
            message = String.format("Invalid UUID format: '%s'", ex.getValue());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                message
        );
        problem.setTitle("Invalid Parameter");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("parameter", ex.getName());
        problem.setProperty("rejectedValue", ex.getValue());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Message not readable: {}", ex.getMessage());
        String message = "Malformed JSON request or invalid field value";
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null) {
            if (cause.getMessage().contains("Cannot deserialize value of type")) {
                message = "Invalid value for enum field. " + extractEnumError(cause.getMessage());
            }
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                message
        );
        problem.setTitle("Invalid Request Body");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private String extractEnumError(String message) {
        if (message.contains("PolicyScope")) {
            return "Valid scopes are: GLOBAL, TENANT, API_KEY, IP, USER";
        } else if (message.contains("Algorithm")) {
            return "Valid algorithms are: TOKEN_BUCKET, FIXED_WINDOW, SLIDING_LOG";
        } else if (message.contains("FailMode")) {
            return "Valid fail modes are: FAIL_OPEN, FAIL_CLOSED";
        }
        return "Check the field value against allowed enum values";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        String message = extractDataIntegrityMessage(ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                message
        );
        problem.setTitle("Data Integrity Violation");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private String extractDataIntegrityMessage(DataIntegrityViolationException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return "Data integrity constraint violated";
        }

        if (message.contains("unique constraint") || message.contains("duplicate key")) {
            if (message.contains("tenants") && message.contains("name")) {
                return "A tenant with this name already exists";
            } else if (message.contains("user_policies") && message.contains("user_id")) {
                return "User policy already exists for this user in the tenant";
            }
            return "A resource with these unique fields already exists";
        } else if (message.contains("foreign key constraint") || message.contains("violates foreign key")) {
            return "Referenced resource does not exist";
        }

        return "Data integrity constraint violated";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problem.setTitle("Bad Request");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
