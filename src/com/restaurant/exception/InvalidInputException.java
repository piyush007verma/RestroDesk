package com.restaurant.exception;

/**
 * Thrown when the user typed something that is not allowed (empty name, negative price,
 * a duplicate item name, ...). The message is written for the user, so screens can show it as it is.
 */
public class InvalidInputException extends Exception {

    public InvalidInputException(String message) {
        super(message);
    }
}
