package com.tailorcards.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class StockConflictException extends RuntimeException {

    public StockConflictException(String message) {
        super(message);
    }

    public StockConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
