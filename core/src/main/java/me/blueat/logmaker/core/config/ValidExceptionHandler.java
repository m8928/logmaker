package me.blueat.logmaker.core.config;

import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.Result;
import org.springframework.http.ResponseEntity;
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
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        return Result.createResultSet(Result.Type.ERROR, "Validation failed", errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleAllExceptions(Exception ex) {
        log.error("An unexpected error occurred", ex);
        return Result.createResultSet(Result.Type.ERROR, "An unexpected internal server error occurred");
    }
}
