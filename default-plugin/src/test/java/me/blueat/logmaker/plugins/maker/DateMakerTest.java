package me.blueat.logmaker.plugins.maker;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DateMakerTest {

    @Test
    void getData() {
        // Given
        Map<String, Object> args = new HashMap<>();
        args.put("format", "yyyy-MM-dd");
        DateMaker dateMaker = new DateMaker("testMaker", "date", args);

        // When
        String data = dateMaker.getData();

        // Then
        assertNotNull(data);
        assertDoesNotThrow(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.parse(data);
        });
    }

    @Test
    void update() {
        // Given
        Map<String, Object> initialArgs = new HashMap<>();
        initialArgs.put("format", "yyyy-MM-dd");
        DateMaker dateMaker = new DateMaker("testMaker", "date", initialArgs);

        // When
        Map<String, Object> newArgs = new HashMap<>();
        newArgs.put("format", "MM/dd/yyyy");
        dateMaker.update(newArgs);

        // Then
        assertEquals("MM/dd/yyyy", dateMaker.getFormat());
    }
}
