package me.blueat.logmaker.plugin.api.sender;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SenderArgs {
    private Class<?> type;
    private String description;
    private boolean required = true;

    public static String toString(Object o) {
        return o.toString();
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> toList(Object o) {
        return (List<T>) o;
    }

    public static int toInt(Object o) {
        return Integer.parseInt(o.toString());
    }

    public static long toLong(Object o) {
        return Long.parseLong(o.toString());
    }

    public static boolean toBoolean(Object o) {
        return (Boolean) o;
    }
}
