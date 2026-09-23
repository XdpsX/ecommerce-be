package com.xdpsx.ecommerce.dtos.common;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xdpsx.ecommerce.constants.messages.APIMessage;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
public class ErrorDTO {
    private int status;
    private String message;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Object[] args;

    public ErrorDTO(HttpStatus status, String message, Object... args) {
        this.status = status.value();
        this.message = message;
        this.args = args;
    }

    public ErrorDTO(HttpStatus status, APIMessage apiMessage, Object... args) {
        this.status = status.value();
        this.message = apiMessage.message();
        this.args = args;
    }
}
