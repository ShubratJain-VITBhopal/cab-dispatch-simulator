package com.cabdispatch.exception;

/**
 * Custom exception thrown when ride parameters or rider details fail basic validation.
 */
public class InvalidRequestException extends Exception {
    public InvalidRequestException(String message) {
        super(message);
    }
}
