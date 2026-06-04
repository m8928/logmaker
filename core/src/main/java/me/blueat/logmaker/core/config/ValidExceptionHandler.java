package me.blueat.logmaker.core.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
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
    public void handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String uri = request.getRequestURI();
        // API paths and already-forwarded SPA fallbacks return 404 JSON instead of entering a forward loop.
        if (uri.startsWith("/api/") || "/index.html".equals(uri) || request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json");
            response.getWriter().write("{\"type\":\"ERROR\",\"message\":\"Not found\"}");
            return;
        }
        forwardToSpa(request, response, HttpServletResponse.SC_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleAllExceptions(Exception ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            if ("/index.html".equals(uri) || request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return null;
            }
            try {
                forwardToSpa(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (ServletException | IOException forwardException) {
                log.error("Failed to forward non-API error to SPA: {}", uri, forwardException);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            return null;
        }
        log.error("API error: {} {}", request.getMethod(), uri, ex);
        return Result.createResultSet(Result.Type.ERROR, "An unexpected internal server error occurred");
    }

    private void forwardToSpa(HttpServletRequest request, HttpServletResponse response, int missingDispatcherStatus)
            throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/index.html");
        if (dispatcher == null) {
            response.sendError(missingDispatcherStatus);
            return;
        }
        dispatcher.forward(request, response);
    }
}
