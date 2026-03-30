package p2ps.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Catches all exceptions and returns a clean JSON response
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Logger used to record internal errors secretly on the server
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        //Log the full error internally (for the backend team to see)
        logger.error("Unhandled exception occurred:", ex);

        //Return a safe, generic message to the frontend (hiding server secrets)
        ErrorResponse errorResponse = new ErrorResponse(
                "Internal Server Error",
                "An unexpected error occurred."
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
