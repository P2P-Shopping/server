package com.p2ps.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AiProcessingExceptionTest {

    @Test
    void messageOnly() {
        AiProcessingException ex = new AiProcessingException("processing failed");
        assertThat(ex.getMessage()).isEqualTo("processing failed");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ex.getRetryAfterSeconds()).isNull();
    }

    @Test
    void messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        AiProcessingException ex = new AiProcessingException("failed", cause);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void messageWithStatus() {
        AiProcessingException ex = new AiProcessingException("failed", HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getRetryAfterSeconds()).isNull();
    }

    @Test
    void messageAndCauseWithStatus() {
        RuntimeException cause = new RuntimeException("root");
        AiProcessingException ex = new AiProcessingException("failed", cause, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void messageWithStatusAndRetryAfterSeconds() {
        AiProcessingException ex = new AiProcessingException("failed", null, HttpStatus.TOO_MANY_REQUESTS, 30L);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getRetryAfterSeconds()).isEqualTo(30L);
    }

    @Test
    void messageAndCauseWithStatusAndRetryAfterSeconds() {
        RuntimeException cause = new RuntimeException("root");
        AiProcessingException ex = new AiProcessingException("failed", cause, HttpStatus.TOO_MANY_REQUESTS, 60L);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getRetryAfterSeconds()).isEqualTo(60L);
    }

    @Test
    void defaultStatusShouldBeUnprocessableContent() {
        AiProcessingException ex = new AiProcessingException("test");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void retryAfterSecondsCanBeNull() {
        AiProcessingException ex = new AiProcessingException("test", HttpStatus.BAD_REQUEST);
        assertThat(ex.getRetryAfterSeconds()).isNull();
    }
}
