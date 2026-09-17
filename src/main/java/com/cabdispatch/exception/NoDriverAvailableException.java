package com.cabdispatch.exception;

/**
 * Custom exception thrown when a ride request cannot be serviced because
 * all drivers in the pool are currently BUSY.
 */
public class NoDriverAvailableException extends Exception {
    public NoDriverAvailableException(String message) {
        super(message);
    }
}
