package com.p2ps.exception;

import com.p2ps.lists.exception.ItemNotFoundException;
import com.p2ps.lists.exception.ListAccessDeniedException;
import com.p2ps.lists.exception.ListUserNotFoundException;
import com.p2ps.lists.exception.ListValidationException;
import com.p2ps.lists.exception.ShoppingListNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.Map;

// Catches all exceptions and returns a clean JSON response
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERR_STR = "error";
    private static final String MSG_STR = "message";
    private static final String VALIDATION_ERROR = "Validation Error";

    // Logger used to record internal errors secretly on the server
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Registration Failed",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT); // 409 Conflict
    }

    // Prinde erorile de la @Valid (ex: parola prea scurta)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Extragem primul mesaj de eroare definit in DTO
        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("Validation failed");

        ErrorResponse errorResponse = new ErrorResponse(
                VALIDATION_ERROR,
                errorMessage
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST); // 400 Bad Request
    }

    @ExceptionHandler(ListValidationException.class)
    public ResponseEntity<ErrorResponse> handleListValidationException(ListValidationException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                VALIDATION_ERROR,
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ItemNotFoundException.class, ShoppingListNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFoundExceptions(RuntimeException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Resource Not Found",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ListAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleListAccessDeniedException(ListAccessDeniedException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Forbidden",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ListUserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleListUserNotFoundException(ListUserNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Unauthorized",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        logger.warn("Upload rejected because file exceeds size limit: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of(
                        ERR_STR, "File Too Large",
                        MSG_STR, "Maximum allowed file size is 5MB"
                ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingServletRequestPart(MissingServletRequestPartException ex) {
        logger.warn("Missing multipart request part: {}", ex.getRequestPartName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        ERR_STR, "Bad Request",
                        MSG_STR, "Missing file part"
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String parameterName = ex.getName();
        Class<?> requiredTypeClass = ex.getRequiredType();
        String requiredType = requiredTypeClass == null ? "valid value" : requiredTypeClass.getSimpleName();
        String details = "Invalid value for '" + parameterName + "'. Expected " + requiredType + ".";

        ErrorResponse errorResponse = new ErrorResponse(
                VALIDATION_ERROR,
                details
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AiProcessingException.class)
    public ResponseEntity<Map<String, String>> handleAiProcessingException(AiProcessingException ex) {
        logger.error("AI Processing failed: {}", ex.getMessage(), ex);

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put(MSG_STR, "AI Processing Failed");
        errorResponse.put("details", ex.getMessage());

        return ResponseEntity.status(ex.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        // avoid NPE when tests (or callers) pass null
        if (ex == null) {
            ErrorResponse error = new ErrorResponse(
                    "Internal Server Error",
                    "An unexpected error occurred."
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }

        //Log the full error internally (for the backend team to see)
        logger.error("Unhandled exception occurred:", ex);

        //Return a safe, generic message to the frontend (hiding server secrets)
        ErrorResponse errorResponse = new ErrorResponse(
                "Internal Server Error",
                "An unexpected error occurred."
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleAuthenticationError(Exception ex) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED) // Trimitem 401 în loc de 500
                .body(Map.of(
                        ERR_STR, "Unauthorized",
                        MSG_STR, "Invalid email or password" // Mesaj generic, sigur
                ));
    }

}
