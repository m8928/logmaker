package me.blueat.logmaker.core.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.blueat.logmaker.core.model.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidExceptionHandlerTest {

    private final ValidExceptionHandler handler = new ValidExceptionHandler();

    @Test
    void forwardsNonApiExceptionsToSpa() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestURI()).thenReturn("/scenario");
        when(request.getRequestDispatcher("/index.html")).thenReturn(dispatcher);

        ResponseEntity<Result> result = handler.handleAllExceptions(new RuntimeException("boom"), request, response);

        assertNull(result);
        verify(dispatcher).forward(request, response);
    }

    @Test
    void sendsServerErrorWhenSpaForwardFails() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestURI()).thenReturn("/scenario");
        when(request.getRequestDispatcher("/index.html")).thenReturn(dispatcher);
        doThrow(new java.io.IOException("forward failed")).when(dispatcher).forward(request, response);

        ResponseEntity<Result> result = handler.handleAllExceptions(new RuntimeException("boom"), request, response);

        assertNull(result);
        verify(response).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    void returnsNotFoundWhenIndexResourceIsMissing() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(request.getRequestURI()).thenReturn("/index.html");
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        handler.handleNoResourceFound(mock(NoResourceFoundException.class), request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
        verify(request, never()).getRequestDispatcher("/index.html");
        assertTrue(body.toString().contains("Not found"));
    }

    @Test
    void genericIndexFailuresDoNotForwardToIndexAgain() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/index.html");

        ResponseEntity<Result> result = handler.handleAllExceptions(new RuntimeException("boom"), request, response);

        assertNull(result);
        verify(response).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(request, never()).getRequestDispatcher("/index.html");
    }

    @Test
    void returnsApiErrorsAsJsonResult() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/api/log");
        when(request.getMethod()).thenReturn("GET");

        ResponseEntity<Result> result = handler.handleAllExceptions(new RuntimeException("boom"), request, response);

        assertNotNull(result);
        assertEquals(Result.Type.ERROR, result.getBody().getType());
    }
}
