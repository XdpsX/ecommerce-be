package com.xdpsx.ecommerce.common.error;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorDTO handleAccessDeniedException(AccessDeniedException e) {
        log.error(e.getMessage(), e);
        return new ErrorDTO(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDTO handleBadCredentialsException(BadCredentialsException e) {
        log.error(e.getMessage(), e); // e.getMessage() = "Bad credentials"
        return new ErrorDTO(HttpStatus.UNAUTHORIZED, EMessage.BAD_CREDENTIALS);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDetailsDTO handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error(e.getMessage(), e);
        ErrorDetailsDTO error = new ErrorDetailsDTO(HttpStatus.BAD_REQUEST, EMessage.BAD_VALIDATION);

        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .filter(fieldError -> fieldError.getDefaultMessage() != null)
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (existingMessage, newMessage) -> existingMessage + "; " + newMessage));
        error.setFieldErrors(fieldErrors);
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorDTO handleException(Exception e) {
        log.error(e.getMessage(), e);
        return new ErrorDTO(HttpStatus.INTERNAL_SERVER_ERROR, EMessage.SERVER_ERROR);
    }
}
