package com.cristian.moneymanager.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Error with a machine-readable code so the front-end can react (e.g. show a
 * "resend activation email" button) instead of parsing the message text.
 * Answered as {"message": ..., "code": ...}.
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
