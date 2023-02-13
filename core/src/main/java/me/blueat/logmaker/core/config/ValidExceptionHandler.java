package me.blueat.logmaker.core.config;

import lombok.extern.slf4j.Slf4j;
import me.blueat.logmaker.core.model.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ValidExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ResponseEntity<Result> validException(
            MethodArgumentNotValidException ex) {
        return Result.createResultSet(Result.Type.ERROR, ex.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }
}
