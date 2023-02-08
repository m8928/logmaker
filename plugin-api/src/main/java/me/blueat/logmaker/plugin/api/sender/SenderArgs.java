package me.blueat.logmaker.plugin.api.sender;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SenderArgs {
    private Class type;
    private String description;

    public Class getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o){
        boolean isT = false;
        SenderArgs senderArgs = (SenderArgs)o;
        if(senderArgs.type.equals(this.type)) {
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
