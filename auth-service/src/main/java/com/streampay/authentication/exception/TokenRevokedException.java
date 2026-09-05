package com.streampay.authentication.exception;

public class TokenRevokedException extends RuntimeException {

    public TokenRevokedException(String message) {
        super(message);
    }
}