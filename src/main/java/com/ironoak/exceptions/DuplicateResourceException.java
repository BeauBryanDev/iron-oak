package com.ironoak.exceptions;

/** A unique business key (SKU, model code, service code) is already taken. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resource, String field, Object value) {
        super(resource + " with " + field + " '" + value + "' already exists");
    }
}
