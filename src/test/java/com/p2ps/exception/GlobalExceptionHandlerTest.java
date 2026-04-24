package com.p2ps.exception;

import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ListValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        Map<String, String> body = resp.getBody();
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
        Map<String, String> body = resp.getBody();
        assertThat(body).containsEntry("error", "Unauthorized");
        assertThat(body).containsEntry("message", "Invalid email or password");
    }

    @Test
    void handleMaxUploadSizeExceededException() {
        var resp = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(5L * 1024 * 1024));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(resp.getBody()).containsEntry("error", "File Too Large");
        assertThat(resp.getBody()).containsEntry("message", "Maximum allowed file size is 5MB");
    }

    @Test
    void handleMissingServletRequestPartException() {
        var resp = handler.handleMissingServletRequestPart(new MissingServletRequestPartException("file"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).containsEntry("error", "Bad Request");
        assertThat(resp.getBody()).containsEntry("message", "Missing file part");
    }

    @Test
    void handleMethodArgumentTypeMismatch_returnsBadRequest() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "PUT_STORE_UUID_HERE",
                java.util.UUID.class,
                "storeId",
                null,
                null
        );

        var resp = handler.handleMethodArgumentTypeMismatch(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).isEqualTo("Validation Error");
        assertThat(resp.getBody().getDetails()).isEqualTo("Invalid value for 'storeId'. Expected UUID.");
    }
}
