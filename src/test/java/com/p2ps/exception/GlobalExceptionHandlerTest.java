package com.p2ps.exception;

import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    @Test
    void shouldReturnGenericErrorResponseWhenUnhandledExceptionOccurs() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception simulatedException = new Exception("Database failure!");

        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(simulatedException);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().getMessage());
        assertEquals("An unexpected error occurred.", response.getBody().getDetails());
    }

    @Test
    void shouldReturnSameGenericErrorResponseForNullException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().getMessage());
    }

    @Test
    void shouldReturnBadRequestForListValidationException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response =
                handler.handleListValidationException(new ListValidationException("Item name cannot be empty"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation Error", response.getBody().getMessage());
        assertEquals("Item name cannot be empty", response.getBody().getDetails());
    }

    @Test
    void shouldReturnNotFoundForListResourceExceptions() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response =
                handler.handleNotFoundExceptions(new ItemNotFoundException("Item not found"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Resource Not Found", response.getBody().getMessage());
        assertEquals("Item not found", response.getBody().getDetails());
    }

    @Test
    void shouldReturnForbiddenForListAccessDeniedException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response =
                handler.handleListAccessDeniedException(
                        new ListAccessDeniedException("You do not have permission to edit this item")
                );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Forbidden", response.getBody().getMessage());
        assertEquals("You do not have permission to edit this item", response.getBody().getDetails());
    }
}
