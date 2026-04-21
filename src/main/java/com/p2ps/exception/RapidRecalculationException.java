package com.p2ps.exception;

public class RapidRecalculationException extends RuntimeException {

    public RapidRecalculationException(String message) {
        super(message);
    }

    public RapidRecalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
