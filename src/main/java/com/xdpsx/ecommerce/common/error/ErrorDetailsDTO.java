package com.xdpsx.ecommerce.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ErrorDetailsDTO extends ErrorDTO {
    private Map<String, String> fieldErrors;

    public ErrorDetailsDTO(HttpStatus status, APIMessage apiMessage, Object... args) {
        super(status, apiMessage, args);
    }
}
