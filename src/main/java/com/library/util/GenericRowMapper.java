package com.library.util;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenericRowMapper<T> {

    // Кэш для хранения полей класса: Class -> Map<snake_case_column_name, Field>
    private static final Map<Class<?>, Map<String, Field>> classFieldsCache = new ConcurrentHashMap<>();

    public T mapRow(ResultSet rs, Class<T> clazz) throws SQLException {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            Map<String, Field> fieldMap = getCachedFields(clazz);
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i).toLowerCase();
                Field field = fieldMap.get(columnName);

                if (field != null) {
                    field.setAccessible(true);
                    Object value = rs.getObject(i);

                    if (value != null) {
                        // Обработка особых типов: Enum и LocalDateTime
                        if (field.getType().isEnum() && value instanceof String) {
                            @SuppressWarnings({"unchecked", "rawtypes"})
                            Enum enumValue = Enum.valueOf((Class<Enum>) field.getType(), (String) value);
                            field.set(instance, enumValue);
                        } else if (field.getType().equals(LocalDateTime.class) && value instanceof Timestamp) {
                            field.set(instance, ((Timestamp) value).toLocalDateTime());
                        } else {
                            // Для примитивов и других типов просто сетаем значение
                            field.set(instance, value);
                        }
                    }
                }
            }
            return instance;
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Error mapping row to class " + clazz.getName(), e);
        }
    }

    private Map<String, Field> getCachedFields(Class<?> clazz) {
        return classFieldsCache.computeIfAbsent(clazz, this::extractFields);
    }

    private Map<String, Field> extractFields(Class<?> clazz) {
        Map<String, Field> fieldMap = new HashMap<>();
        Class<?> currentClass = clazz;

        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                String snakeCaseName = camelToSnake(field.getName());
                fieldMap.put(snakeCaseName, field);
            }
            currentClass = currentClass.getSuperclass();
        }
        return fieldMap;
    }

    private String camelToSnake(String camelCase) {
        StringBuilder snakeCase = new StringBuilder();
        for (char c : camelCase.toCharArray()) {
            if (Character.isUpperCase(c)) {
                snakeCase.append('_').append(Character.toLowerCase(c));
            } else {
                snakeCase.append(c);
            }
        }
        return snakeCase.toString();
    }
}
