package me.blueat.logmaker.core.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
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

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Static resource not found (favicon, etc.) — silently redirect to SPA index
        response.sendRedirect("/index.html");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleAllExceptions(Exception ex, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            // Non-API errors — silently forward to SPA
            return null;
        }
        log.error("API error: {} {}", request.getMethod(), uri, ex);
        return Result.createResultSet(Result.Type.ERROR, "An unexpected internal server error occurred");
    }
}
