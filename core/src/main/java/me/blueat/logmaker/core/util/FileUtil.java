package me.blueat.logmaker.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;

@Slf4j
public class FileUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> void saveToFile(T dto, String filePath) {
        try {
            File file = new File(filePath);
            if (!file.getParentFile().exists()) {
                Files.createDirectories(file.getParentFile().toPath());
            }
            mapper.writeValue(new File(filePath), dto);
            log.info("Saved to {}", filePath);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 처리 중 오류 발생", e);
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 오류 발생", e);
        }
    }

    public static <T> T loadFromFile(String filePath, Class<T> valueType) {
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                return createNewInstance(valueType);
            }

            T dto = mapper.readValue(file, valueType);
            log.info("Loaded from file {}", filePath);
            return dto;
        } catch (IOException e) {
            throw new RuntimeException("파일 로드 중 오류 발생", e);
        }
    }

    private static <T> T createNewInstance(Class<T> valueType) {
        try {
            if (valueType.isArray()) {
                @SuppressWarnings("unchecked")
                T array = (T) Array.newInstance(valueType.getComponentType(), 0);
                return array;
            } else {
                // 일반 클래스인 경우 기본 생성자로 생성
                return valueType.getDeclaredConstructor().newInstance();
            }
        } catch (InstantiationException | IllegalAccessException |
                 NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException("새 인스턴스 생성 중 오류 발생: " + e.getMessage(), e);
        }
    }
}
