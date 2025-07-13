package me.blueat.logmaker.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileUtilTest {

    @TempDir
    Path tempDir;

    private Path testFilePath;

    @BeforeEach
    void setUp() {
        testFilePath = tempDir.resolve("test_dto.json");
    }

    @Test
    void saveAndLoadFromFile() {
        // Given
        TestDto testDto = new TestDto("testName", 123);

        // When
        FileUtil.saveToFile(testDto, testFilePath.toString());
        TestDto loadedDto = FileUtil.loadFromFile(testFilePath.toString(), TestDto.class);

        // Then
        assertEquals(testDto.getName(), loadedDto.getName());
        assertEquals(testDto.getValue(), loadedDto.getValue());
    }

    // Helper class for testing
    private static class TestDto {
        private String name;
        private int value;

        public TestDto() {
        }

        public TestDto(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}