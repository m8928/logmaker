package me.blueat.logmaker.core.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result {
    public enum Type {
        SUCCESS, ERROR, VOID
    }

    private Type type;
    private String message;

    public Result(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public Result(Type type) {
        this.type = type;
    }

    public static Result createResultSet(Type type) {
        return new Result(type);

    }
    public static Result createResultSet(Type type, String message) {
        return new Result(type, message);
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }
}
