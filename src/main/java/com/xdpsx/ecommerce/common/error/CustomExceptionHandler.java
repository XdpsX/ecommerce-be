package com.xdpsx.ecommerce.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(InvalidResourceTypeException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorDTO handleInvalidMediaResourceException(InvalidResourceTypeException e) {
        log.error(e.getMessage(), e);
        return new ErrorDTO(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), e.getArgs());
    }

    @ExceptionHandler({DuplicateException.class, InUseException.class, ModifyExclusiveException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDTO handleConflictException(APIException e) {
        log.error(e.getMessage(), e);
        return new ErrorDTO(HttpStatus.CONFLICT, e.getMessage(), e.getArgs());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDTO handleNotFoundException(NotFoundException e) {
        log.error(e.getMessage(), e);
        return new ErrorDTO(HttpStatus.NOT_FOUND, e.getMessage(), e.getArgs());
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO handleBadRequestException(BadRequestException e) {
        log.error(e.getMessage(), e);
        return new ErrorDTO(HttpStatus.BAD_REQUEST, e.getMessage(), e.getArgs());
    }
}
