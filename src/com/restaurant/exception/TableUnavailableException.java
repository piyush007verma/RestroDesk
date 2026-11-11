package com.restaurant.exception;

/**
 * Thrown when a table cannot be assigned or released because of its current state
 * (for example: assigning a table that someone else just took). The message is user-friendly.
 */
public class TableUnavailableException extends Exception {

    public TableUnavailableException(String message) {
        super(message);
    }
}
