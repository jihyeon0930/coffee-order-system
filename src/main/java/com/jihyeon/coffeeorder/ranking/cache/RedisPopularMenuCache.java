package com.jihyeon.coffeeorder.ranking.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jihyeon.coffeeorder.ranking.dto.PopularMenuResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisPopularMenuCache implements PopularMenuCache {

    public static final String CACHE_KEY = "popular-menus:v2:last-7-days:top:3";

    private static final Logger log = LoggerFactory.getLogger(RedisPopularMenuCache.class);
    private static final TypeReference<List<PopularMenuResponse>> VALUE_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisPopularMenuCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${ranking.cache.ttl:5m}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<List<PopularMenuResponse>> get() {
        try {
            String value = redisTemplate.opsForValue().get(CACHE_KEY);
            return value == null ? Optional.empty() : Optional.of(objectMapper.readValue(value, VALUE_TYPE));
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("인기 메뉴 Redis 캐시 조회에 실패하여 DB 조회로 대체합니다.", exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(List<PopularMenuResponse> menus) {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(menus), ttl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("인기 메뉴 Redis 캐시 저장에 실패했습니다.", exception);
        }
    }

    @Override
    public void evict() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (DataAccessException exception) {
            log.warn("인기 메뉴 Redis 캐시 삭제에 실패했습니다.", exception);
        }
    }
}
