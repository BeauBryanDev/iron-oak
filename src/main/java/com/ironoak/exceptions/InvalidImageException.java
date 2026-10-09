package com.ironoak.exceptions;

/** The uploaded file is missing, empty, or not an image the decoder can read. */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
