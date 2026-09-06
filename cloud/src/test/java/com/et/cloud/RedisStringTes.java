package com.et.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RedisStringTes {

    @Test
    public void testRedisStringOperations() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        Map<String, String> values = new HashMap<>();
        ValueOperations<String, String> valueOps = inMemoryValueOperations(values);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.doAnswer(invocation -> values.remove(invocation.getArgument(0)) != null)
                .when(stringRedisTemplate).delete(org.mockito.ArgumentMatchers.anyString());

        String key = "testKey";
        String value = "testValue";


        valueOps.set(key, value);
        String storedValue = valueOps.get(key);
        assertEquals(value, storedValue, "存储的值与预期不一致");


        String updatedValue = "updatedValue";
        valueOps.set(key, updatedValue);
        storedValue = valueOps.get(key);
        assertEquals(updatedValue, storedValue, "更新后的值与预期不一致");


        storedValue = valueOps.get(key);
        assertNotNull(storedValue, "查询的值为空");
        assertEquals(updatedValue, storedValue, "查询的值与预期不一致");


        stringRedisTemplate.delete(key);
        storedValue = valueOps.get(key);
        assertNull(storedValue, "删除后的值不为空");
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> inMemoryValueOperations(Map<String, String> values) {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        when(valueOps.get(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        return valueOps;
    }
}
