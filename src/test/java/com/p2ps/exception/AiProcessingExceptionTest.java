package com.p2ps.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiProcessingExceptionTest {

    @Test
    void messageOnly() {
        AiProcessingException ex = new AiProcessingException("processing failed");
        assertThat(ex.getMessage()).isEqualTo("processing failed");
    }

    @Test
    void messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        AiProcessingException ex = new AiProcessingException("failed", cause);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
