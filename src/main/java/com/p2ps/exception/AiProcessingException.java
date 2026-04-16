package com.p2ps.exception;

import org.springframework.http.HttpStatus;

public class AiProcessingException extends RuntimeException {

  private HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;

  public AiProcessingException(String message) {
    super(message);
  }

  public AiProcessingException(String message, Throwable cause) {
    super(message, cause);
  }

  public AiProcessingException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }

  public AiProcessingException(String message, Throwable cause, HttpStatus status) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}