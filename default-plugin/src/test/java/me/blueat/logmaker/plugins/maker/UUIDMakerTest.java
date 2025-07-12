package me.blueat.logmaker.plugins.maker;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UUIDMakerTest {

    @Test
    void getData() {
        // Given
        UUIDMaker uuidMaker = new UUIDMaker("testMaker", "uuid");
        uuidMaker.getThread().start();

        // When
        String data = uuidMaker.getData();

        // Then
        assertNotNull(data);
        assertDoesNotThrow(() -> UUID.fromString(data));

        uuidMaker.getThread().interrupt();
    }

    @Test
    void isThread() {
        // Given
        UUIDMaker uuidMaker = new UUIDMaker("testMaker", "uuid");

        // When & Then
        assertTrue(uuidMaker.isThread());
    }
}
