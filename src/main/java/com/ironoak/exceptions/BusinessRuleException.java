package com.ironoak.exceptions;

/** The request is valid but breaks a business rule (wrong state, not eligible, already done). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
