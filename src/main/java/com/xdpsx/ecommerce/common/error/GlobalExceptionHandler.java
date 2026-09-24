package com.xdpsx.ecommerce.common.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * The single MVC boundary that maps application and framework errors to controlled RFC 9457 problem responses.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC client errors (malformed JSON, unsupported media
 * types, missing parameters, type mismatches, unsupported methods, ...) keep their framework-defined statuses instead
 * of falling through to a generic 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Object> handleApplicationException(ApplicationException ex, WebRequest request) {
        log.debug("Application error {}: {}", ex.getCode(), ex.getParameters());
        ProblemDetail problem = ApiProblemFactory.create(ex.getCode(), ex.getParameters());
        applyInstance(problem, request);
        return new ResponseEntity<>(problem, HttpStatus.valueOf(problem.getStatus()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        log.debug("Access denied");
        ProblemDetail problem = ApiProblemFactory.create(ErrorCode.ACCESS_DENIED);
        applyInstance(problem, request);
        return new ResponseEntity<>(problem, ApiProblemFactory.statusFor(ErrorCode.ACCESS_DENIED));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.debug("Authentication failed");
        ProblemDetail problem = ApiProblemFactory.create(ErrorCode.INVALID_CREDENTIALS);
        applyInstance(problem, request);
        return new ResponseEntity<>(problem, ApiProblemFactory.statusFor(ErrorCode.INVALID_CREDENTIALS));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ApiProblemFactory.create(ErrorCode.INTERNAL_ERROR);
        applyInstance(problem, request);
        return new ResponseEntity<>(problem, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            violations.add(new FieldViolation(fieldError.getField(), fieldError.getCode(), fieldError.getDefaultMessage()));
        }
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            violations.add(new FieldViolation(null, globalError.getCode(), globalError.getDefaultMessage()));
        }

        ProblemDetail problem = ApiProblemFactory.create(ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", violations);
        applyInstance(problem, request);
        return new ResponseEntity<>(problem, ApiProblemFactory.statusFor(ErrorCode.VALIDATION_FAILED));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code =
                statusCode.value() == HttpStatus.NOT_FOUND.value() ? ErrorCode.RESOURCE_NOT_FOUND : ErrorCode.MALFORMED_REQUEST;
        ProblemDetail problem;
        if (body instanceof ProblemDetail frameworkProblem) {
            problem = frameworkProblem;
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty("code", code.name());
            }
        } else {
            problem = ApiProblemFactory.create(code);
            problem.setStatus(statusCode.value());
        }
        applyInstance(problem, request);
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private void applyInstance(ProblemDetail problem, WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            problem.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }
    }
}
