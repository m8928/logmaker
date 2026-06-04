package me.blueat.logmaker.core.util;

public final class TextSizeUtil {
    private TextSizeUtil() {
    }

    public static int utf8Length(String value) {
        int length = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x7F) {
                length++;
            } else if (c <= 0x7FF) {
                length += 2;
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                    length += 4;
                    i++;
                } else {
                    length++;
                }
            } else if (Character.isLowSurrogate(c)) {
                length++;
            } else {
                length += 3;
            }
        }
        return length;
    }
}
