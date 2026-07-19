package com.jihyeon.coffeeorder.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jihyeon.coffeeorder.ranking.cache.RedisPopularMenuCache;
import com.jihyeon.coffeeorder.ranking.dto.PopularMenuResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisPopularMenuCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisPopularMenuCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisPopularMenuCache(redisTemplate, new ObjectMapper(), Duration.ofMinutes(5));
    }

    @Test
    void storeCacheWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<PopularMenuResponse> menus = List.of(new PopularMenuResponse(1, 1L, "Americano", 3, 2));

        cache.put(menus);

        verify(valueOperations).set(
                RedisPopularMenuCache.CACHE_KEY,
                "[{\"rank\":1,\"menuId\":1,\"menuName\":\"Americano\",\"totalQuantity\":3,\"orderCount\":2}]",
                Duration.ofMinutes(5)
        );
    }

    @Test
    void returnCachedValue() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisPopularMenuCache.CACHE_KEY)).thenReturn(
                "[{\"rank\":1,\"menuId\":1,\"menuName\":\"Americano\",\"totalQuantity\":3,\"orderCount\":2}]"
        );

        assertThat(cache.get()).contains(List.of(new PopularMenuResponse(1, 1L, "Americano", 3, 2)));
    }

    @Test
    void redisFailureIsHandledAsCacheMiss() {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThat(cache.get()).isEmpty();
        assertThatCode(() -> cache.put(List.of())).doesNotThrowAnyException();
        assertThatCode(cache::evict).doesNotThrowAnyException();
    }
}
