package com.p2ps.exception;

public class AiProcessingException extends RuntimeException {
  public AiProcessingException(String message) {
    super(message);
  }

  public AiProcessingException(String message, Throwable cause) {
    super(message, cause);
  }
}