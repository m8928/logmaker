package me.blueat.logmaker.core.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class ValidExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error.getObjectName();
            if (error instanceof FieldError fieldError) {
                fieldName = fieldError.getField();
            }
            errors.put(fieldName, error.getDefaultMessage());
        });
        return Result.createResultSet(Result.Type.ERROR, "Validation failed", errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleAllExceptions(Exception ex, HttpServletRequest request) {
        // Only handle API errors — let non-API requests fall through to SPA routing
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            throw new RuntimeException(ex);
        }
        log.error("An unexpected error occurred: {}", uri, ex);
        return Result.createResultSet(Result.Type.ERROR, "An unexpected internal server error occurred");
    }
}
