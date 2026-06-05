package me.blueat.logmaker.core.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSizeUtilTest {
    @Test
    void utf8LengthReturnsZeroForNull() {
        assertEquals(0, TextSizeUtil.utf8Length(null));
    }

    @Test
    void utf8Length_matchesJdkEncodingLengthForValidCodePoints() {
        String value = "ascii-\u00E9-\u20AC-\uD83D\uDE00";

        assertEquals(value.getBytes(StandardCharsets.UTF_8).length, TextSizeUtil.utf8Length(value));
    }

    @Test
    void utf8Length_countsUnpairedSurrogateAsReplacementCharacter() {
        assertEquals(3, TextSizeUtil.utf8Length("\uD83D"));
        assertEquals(3, TextSizeUtil.utf8Length("\uDE00"));
    }
}
