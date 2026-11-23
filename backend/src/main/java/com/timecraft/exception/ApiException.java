package com.timecraft.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for expected business errors. The message is safe to show to clients.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
