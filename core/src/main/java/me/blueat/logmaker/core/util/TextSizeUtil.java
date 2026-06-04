package me.blueat.logmaker.core.util;

public final class TextSizeUtil {
    private TextSizeUtil() {
    }

    public static int utf8Length(String value) {
        int length = 0;
        int i = 0;
        while (i < value.length()) {
            int codePoint = value.codePointAt(i);
            if (codePoint <= 0x7F) {
                length++;
            } else if (codePoint <= 0x7FF) {
                length += 2;
            } else if (codePoint <= 0xFFFF) {
                length += 3;
            } else {
                length += 4;
            }
            i += Character.charCount(codePoint);
        }
        return length;
    }
}
