package p2ps.exception;

import com.p2ps.exception.ErrorResponse;
import com.p2ps.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GlobalExceptionHandlerTest {

    @Test
    public void testHandleGlobalException() {

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception simulatedException = new Exception("Database failure!");


        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(simulatedException);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().getMessage());
        assertEquals("An unexpected error occurred.", response.getBody().getDetails());
    }
}