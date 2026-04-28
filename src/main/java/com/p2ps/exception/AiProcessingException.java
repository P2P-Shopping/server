package com.p2ps.exception;

import org.springframework.http.HttpStatus;

public class AiProcessingException extends RuntimeException {

  private final HttpStatus status;
  private final Long retryAfterSeconds;

  public AiProcessingException(String message) {
    super(message);
    this.status = HttpStatus.UNPROCESSABLE_CONTENT;
    this.retryAfterSeconds = null;
  }

  public AiProcessingException(String message, Throwable cause) {
    super(message, cause);
    this.status = HttpStatus.UNPROCESSABLE_CONTENT;
    this.retryAfterSeconds = null;
  }

  public AiProcessingException(String message, HttpStatus status) {
    super(message);
    this.status = status;
    this.retryAfterSeconds = null;
  }

  public AiProcessingException(String message, Throwable cause, HttpStatus status) {
    super(message, cause);
    this.status = status;
    this.retryAfterSeconds = null;
  }

  public AiProcessingException(String message, Throwable cause, HttpStatus status, Long retryAfterSeconds) {
    super(message, cause);
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public Long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
