package com.p2ps.exception;

import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ListValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleUserAlreadyExists() {
        UserAlreadyExistsException ex = new UserAlreadyExistsException("exists");
        var resp = handler.handleUserAlreadyExists(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getMessage()).isEqualTo("Registration Failed");
        assertThat(resp.getBody().getDetails()).isEqualTo("exists");
    }

    @Test
    void handleValidationErrors_extractsMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(br.getAllErrors()).thenReturn(List.of(new ObjectError("obj", "bad input")));
        when(ex.getBindingResult()).thenReturn(br);

        var resp = handler.handleValidationErrors(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getDetails()).isEqualTo("bad input");
    }

    @Test
    void handleListValidationException() {
        ListValidationException ex = new ListValidationException("invalid list");
        var resp = handler.handleListValidationException(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getDetails()).isEqualTo("invalid list");
    }

    @Test
    void handleNotFoundExceptions() {
        ItemNotFoundException ex = new ItemNotFoundException("item missing");
        var resp = handler.handleNotFoundExceptions(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getDetails()).isEqualTo("item missing");
    }

    @Test
    void handleListAccessDeniedException() {
        ListAccessDeniedException ex = new ListAccessDeniedException("no access");
        var resp = handler.handleListAccessDeniedException(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getDetails()).isEqualTo("no access");
    }

    @Test
    void handleListUserNotFoundException() {
        ListUserNotFoundException ex = new ListUserNotFoundException("user missing");
        var resp = handler.handleListUserNotFoundException(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getDetails()).isEqualTo("user missing");
    }

    @Test
    void handleAiProcessingException_returnsMap() {
        AiProcessingException ex = new AiProcessingException("ai bad");
        var resp = handler.handleAiProcessingException(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String,String> body = resp.getBody();
        assertThat(body).containsEntry("message", "AI Processing Failed");
        assertThat(body).containsEntry("details", "ai bad");
    }

    @Test
    void handleGlobalException_nullAndNonNull() {
        var respNull = handler.handleGlobalException(null);
        assertThat(respNull.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respNull.getBody().getDetails()).isEqualTo("An unexpected error occurred.");

        Exception ex = new Exception("boom");
        var resp = handler.handleGlobalException(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getDetails()).isEqualTo("An unexpected error occurred.");
    }

    @Test
    void handleAuthenticationError() {
        BadCredentialsException ex = new BadCredentialsException("x");
        var resp = handler.handleAuthenticationError(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<String,String> body = resp.getBody();
        assertThat(body).containsEntry("error", "Unauthorized");
        assertThat(body).containsEntry("message", "Invalid email or password");
    }
}
