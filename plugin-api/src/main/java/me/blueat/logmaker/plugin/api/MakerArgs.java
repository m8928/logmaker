package me.blueat.logmaker.plugin.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MakerArgs {
    public enum Type {
        STRING, INT, LONG, DOUBLE, LIST, BOOLEAN
    }

    private Type type;
    private String description;

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o){
        boolean isT = false;
        MakerArgs makerArgs = (MakerArgs)o;
        if(makerArgs.type.equals(this.type)){
            isT = true;
        }
        return isT;
    }

    public static String toString(Object o) {
        return o.toString();
    }

    public static List toList(Object o) {
        return (List) o;
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
