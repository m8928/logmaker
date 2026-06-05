package me.blueat.logmaker.core.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void testLoadFromFile_nonExistentFile_returnsDefault() {
        // Given: a path that does not exist
        String nonExistentPath = tempDir.resolve("nonexistent.json").toString();

        // When: load a non-array class
        TestDto result = FileUtil.loadFromFile(nonExistentPath, TestDto.class);

        // Then: returns new instance (default constructor)
        assertNotNull(result);
    }

    @Test
    void testLoadFromFile_nonExistentFile_returnsEmptyArray() {
        // Given
        String nonExistentPath = tempDir.resolve("nonexistent_array.json").toString();

        // When
        TestDto[] result = FileUtil.loadFromFile(nonExistentPath, TestDto[].class);

        // Then: returns empty array
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void testLoadFromFile_emptyFile_returnsDefault() throws IOException {
        Path emptyFile = tempDir.resolve("empty.json");
        Files.writeString(emptyFile, "");

        TestDto result = FileUtil.loadFromFile(emptyFile.toString(), TestDto.class);

        assertNotNull(result);
    }

    @Test
    void testLoadFromFile_emptyFile_returnsEmptyArray() throws IOException {
        Path emptyFile = tempDir.resolve("empty_array.json");
        Files.writeString(emptyFile, "");

        TestDto[] result = FileUtil.loadFromFile(emptyFile.toString(), TestDto[].class);

        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void testLoadFromFile_invalidJson_throwsException() throws IOException {
        // Given: file with invalid JSON content
        Path badJson = tempDir.resolve("bad.json");
        Files.writeString(badJson, "{ this is not valid json }");

        // When & Then
        assertThrows(RuntimeException.class, () ->
                FileUtil.loadFromFile(badJson.toString(), TestDto.class));
    }

    @Test
    void testSaveToFile_createsParentDirectories() {
        // Given: a path with nested directories that don't exist
        Path nestedPath = tempDir.resolve("a/b/c/test.json");
        TestDto testDto = new TestDto("nested", 42);

        // When
        FileUtil.saveToFile(testDto, nestedPath.toString());

        // Then: file was created
        assertTrue(Files.exists(nestedPath));
    }

    @Test
    void testSaveToFile_withSimpleFilenameUsesCurrentDirectory() throws IOException {
        Path simplePath = Paths.get("fileutil-parentless-" + UUID.randomUUID() + ".json");
        try {
            TestDto testDto = new TestDto("simple", 7);

            FileUtil.saveToFile(testDto, simplePath.toString());

            assertTrue(Files.exists(simplePath));
        } finally {
            Files.deleteIfExists(simplePath);
        }
    }

    @Test
    void testSaveToFile_deletesTempFileWhenSerializationFails() throws IOException {
        Path target = tempDir.resolve("failed.json");

        assertThrows(FileUtil.FileOperationException.class, () ->
                FileUtil.saveToFile(new FailingDto(), target.toString()));

        assertFalse(Files.exists(target));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertTrue(files.noneMatch(path ->
                    path.getFileName().toString().startsWith("logmaker-")
                            && path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void testSaveAndLoad_roundTrip() {
        // Given
        TestDto original = new TestDto("roundTrip", 999);

        // When
        FileUtil.saveToFile(original, testFilePath.toString());
        TestDto loaded = FileUtil.loadFromFile(testFilePath.toString(), TestDto.class);

        // Then
        assertEquals(original.getName(), loaded.getName());
        assertEquals(original.getValue(), loaded.getValue());
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

    private static class FailingDto {
        public String getValue() {
            throw new IllegalStateException("serialization failed");
        }
    }
}
