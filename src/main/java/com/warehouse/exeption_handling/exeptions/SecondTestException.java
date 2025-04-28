package com.warehouse.exeption_handling.exeptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "This is second test Exception")
public class SecondTestException extends RuntimeException{
    public SecondTestException(String message) {
        super(message);
    }
}
