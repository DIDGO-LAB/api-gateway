package com.didgo.apigateway.security;

public class InvalidTokenException extends RuntimeException {

    private final String code;

    public InvalidTokenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
