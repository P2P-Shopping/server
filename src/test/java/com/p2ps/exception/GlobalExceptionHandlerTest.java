package com.p2ps.exception;

import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ListValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
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
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        Map<String,String> body = resp.getBody();
        assertThat(body).containsEntry("message", "AI Processing Failed");
        assertThat(body).containsEntry("details", "ai bad");
    }

    @Test
    void shouldReturnGenericErrorResponseWhenUnhandledExceptionOccurs() {
        Exception simulatedException = new Exception("Database failure!");

        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(simulatedException);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().getMessage());
        assertEquals("An unexpected error occurred.", response.getBody().getDetails());
    }

    @Test
    void shouldReturnSameGenericErrorResponseForNullException() {
        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().getMessage());
    }

    @Test
    void shouldReturnBadRequestForListValidationException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleListValidationException(new ListValidationException("Item name cannot be empty"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation Error", response.getBody().getMessage());
        assertEquals("Item name cannot be empty", response.getBody().getDetails());
    }

    @Test
    void shouldReturnNotFoundForListResourceExceptions() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFoundExceptions(new ItemNotFoundException("Item not found"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Resource Not Found", response.getBody().getMessage());
        assertEquals("Item not found", response.getBody().getDetails());
    }

    @Test
    void shouldReturnForbiddenForListAccessDeniedException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleListAccessDeniedException(
                        new ListAccessDeniedException("You do not have permission to edit this item")
                );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Forbidden", response.getBody().getMessage());
        assertEquals("You do not have permission to edit this item", response.getBody().getDetails());
    }

    @Test
    void shouldReturnUnauthorizedForMissingListUser() {
        ResponseEntity<ErrorResponse> response =
                handler.handleListUserNotFoundException(new ListUserNotFoundException("User not found"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
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

    @Test
    void shouldHandleMaxUploadSizeExceededException() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(5 * 1024 * 1024);

        ResponseEntity<Map<String, String>> response =
                handler.handleMaxUploadSizeExceeded(ex);

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("File Too Large", response.getBody().get("error"));

        String message = response.getBody().get("message");
        assertNotNull(message);
        assertTrue(message.contains("5MB") || message.contains("Maximum allowed file size"));
    }

    @Test
    void shouldHandleMissingServletRequestPartException() {
        MissingServletRequestPartException ex =
                new MissingServletRequestPartException("file");

        ResponseEntity<Map<String, String>> response =
                handler.handleMissingServletRequestPart(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals("Missing file part", response.getBody().get("message"));
    }
}
