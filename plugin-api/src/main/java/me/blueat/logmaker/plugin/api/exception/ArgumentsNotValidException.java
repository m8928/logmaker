package me.blueat.logmaker.plugin.api.exception;

import lombok.Getter;

@Getter
public class ArgumentsNotValidException extends RuntimeException {
    private String argumentName;

    public ArgumentsNotValidException() {}

    public ArgumentsNotValidException(String argumentName) {
        this.argumentName = argumentName;
    }
}
