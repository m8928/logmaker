package me.blueat.logmaker.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class FileUtil {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ReentrantLock writeLock = new ReentrantLock();

    public static class FileOperationException extends RuntimeException {
        public FileOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static <T> void saveToFile(T data, String filePath) {
        writeLock.lock();
        Path tempFile = null;
        try {
            Path targetPath = Paths.get(filePath);
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Path tempDirectory = parent != null ? parent : Paths.get(".");
            tempFile = Files.createTempFile(tempDirectory, "logmaker-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), data);
            moveIntoPlace(tempFile, targetPath);
            tempFile = null;
            log.info("Saved to {}", filePath);
        } catch (IOException e) {
            throw new FileOperationException("Failed to save file: " + filePath, e);
        } finally {
            deleteTempFile(tempFile);
            writeLock.unlock();
        }
    }

    private static void moveIntoPlace(Path tempFile, Path targetPath) throws IOException {
        try {
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            log.warn("Atomic move is not supported for {}, falling back to replace move", targetPath);
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.error("Failed to delete temp file: {}", tempFile, e);
        }
    }

    public static <T> T loadFromFile(String filePath, Class<T> valueType) {
        try {
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                return createNewInstance(valueType);
            }

            if (Files.size(path) == 0) {
                log.warn("Storage file is empty, using default value: {}", filePath);
                return createNewInstance(valueType);
            }

            T dto = mapper.readValue(path.toFile(), valueType);
            log.info("Loaded from file {}", filePath);
            return dto;
        } catch (IOException e) {
            throw new FileOperationException("Failed to load file: " + filePath, e);
        }
    }

    private static <T> T createNewInstance(Class<T> valueType) {
        try {
            if (valueType.isArray()) {
                @SuppressWarnings("unchecked")
                T array = (T) Array.newInstance(valueType.getComponentType(), 0);
                return array;
            } else {
                return valueType.getDeclaredConstructor().newInstance();
            }
        } catch (InstantiationException | IllegalAccessException |
                 NoSuchMethodException | InvocationTargetException e) {
            throw new FileOperationException("Failed to create new instance: " + e.getMessage(), e);
        }
    }
}
