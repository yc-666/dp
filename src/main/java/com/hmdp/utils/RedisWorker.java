package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {
    private static final long Begin = 1640995200L;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    public  long nextId(String key){
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long time = nowSeconds - Begin;

        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        long count = stringRedisTemplate.opsForValue().increment("icl" + key + ":" + date);

        return time<<32|count;

    }
}
