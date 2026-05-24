package com.p2ps.exception;

import com.p2ps.lists.exception.InvitationNotFoundException;
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

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Registration Failed",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, jsonHeaders(), HttpStatus.CONFLICT);
    }

    // Prinde erorile de la @Valid (ex: parola prea scurta)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("Validation failed");

        ErrorResponse errorResponse = new ErrorResponse(
                VALIDATION_ERROR,
                errorMessage
        );
        return new ResponseEntity<>(errorResponse, jsonHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ListValidationException.class)
    public ResponseEntity<ErrorResponse> handleListValidationException(ListValidationException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                VALIDATION_ERROR,
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, jsonHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ItemNotFoundException.class, ShoppingListNotFoundException.class, InvitationNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFoundExceptions(RuntimeException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Resource Not Found",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, jsonHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ListAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleListAccessDeniedException(ListAccessDeniedException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "Forbidden",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, jsonHeaders(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ListUserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleListUserNotFoundException(ListUserNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "User Not Found",
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, jsonHeaders(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        logger.warn("Upload rejected because file exceeds size limit: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONTENT_TOO_LARGE)
                .headers(jsonHeaders())
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
                .headers(jsonHeaders())
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
        return new ResponseEntity<>(errorResponse, jsonHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AiProcessingException.class)
    public ResponseEntity<Map<String, String>> handleAiProcessingException(AiProcessingException ex) {
        logger.error("AI Processing failed: {}", ex.getMessage(), ex);

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put(MSG_STR, "AI Processing Failed");
        errorResponse.put("details", ex.getMessage());
        if (ex.getRetryAfterSeconds() != null) {
            errorResponse.put("retryAfterSeconds", String.valueOf(ex.getRetryAfterSeconds()));
        }

        return ResponseEntity.status(ex.getStatus()).headers(jsonHeaders()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        if (ex == null) {
            ErrorResponse error = new ErrorResponse(
                    "Internal Server Error",
                    "An unexpected error occurred."
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).headers(jsonHeaders()).body(error);
        }

        logger.error("Unhandled exception occurred:", ex);

        ErrorResponse errorResponse = new ErrorResponse(
                "Internal Server Error",
                "An unexpected error occurred."
        );

        return new ResponseEntity<>(errorResponse, jsonHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleAuthenticationError(Exception ex) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .headers(jsonHeaders())
                .body(Map.of(
                        ERR_STR, "Unauthorized",
                        MSG_STR, "Invalid email or password"
                ));
    }

}
