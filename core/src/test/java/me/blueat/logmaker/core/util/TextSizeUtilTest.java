package me.blueat.logmaker.core.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSizeUtilTest {
    @Test
    void utf8Length_matchesJdkEncodingLength() {
        String value = "ascii-\u00E9-\u20AC-\uD83D\uDE00-\uD83D";

        assertEquals(value.getBytes(StandardCharsets.UTF_8).length, TextSizeUtil.utf8Length(value));
    }
}
