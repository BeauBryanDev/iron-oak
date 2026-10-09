package com.ironoak.exceptions;

/** The row cannot be removed because other records still reference it. */
public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException(String message) {
        super(message);
    }
}
