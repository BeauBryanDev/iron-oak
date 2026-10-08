package com.ironoak.exceptions;

/** The order request is well-formed but cannot be fulfilled as written. */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
