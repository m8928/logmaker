package me.blueat.logmaker.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result {
    public enum Type {
        SUCCESS, ERROR, VOID
    }

    private Type type;
    private String message;
    private boolean notification = true;

    public Result(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public Result(Type type) {
        this.type = type;
    }

    public static ResponseEntity<Result> createResultSet(Type type) {
        return createResponseEntity(new Result(type));
    }

    public static ResponseEntity<Result> createResultSet(Type type, String message) {
        return createResponseEntity(new Result(type, message));
    }

    public static ResponseEntity<Result> createResultSet(Type type, String message, boolean notification) {
        return createResponseEntity(new Result(type, message).withNotification(notification));
    }

    public static ResponseEntity<Result> createResponseEntity(Result result) {
        if (result.type == Type.SUCCESS) {
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
        else if (result.type == Type.ERROR) {
            return new ResponseEntity<>(result, HttpStatus.NOT_ACCEPTABLE);
        }
        return new ResponseEntity<>(result, HttpStatus.ACCEPTED);
    }

    public Result withNotification(boolean notification) {
        this.notification = notification;
        return this;
    }
}
