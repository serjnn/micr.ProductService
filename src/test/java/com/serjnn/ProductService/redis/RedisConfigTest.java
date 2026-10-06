package com.serjnn.ProductService.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisConfigTest {

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    @DisplayName("Should return configured CacheErrorHandler that swallows exceptions gracefully")
    void shouldHandleCacheErrorsGracefully() {
        CacheErrorHandler errorHandler = redisConfig.errorHandler();
        assertNotNull(errorHandler);

        Cache mockCache = mock(Cache.class);
        when(mockCache.getName()).thenReturn("discounts");

        RuntimeException testException = new RuntimeException("Redis connection refused");

        // Verify that none of the error handler methods re-throw the exception
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(testException, mockCache, 1L));
        assertDoesNotThrow(() -> errorHandler.handleCachePutError(testException, mockCache, 1L, "val"));
        assertDoesNotThrow(() -> errorHandler.handleCacheEvictError(testException, mockCache, 1L));
        assertDoesNotThrow(() -> errorHandler.handleCacheClearError(testException, mockCache));

        // Test with null cache as well
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(testException, null, 1L));
        assertDoesNotThrow(() -> errorHandler.handleCachePutError(testException, null, 1L, "val"));
        assertDoesNotThrow(() -> errorHandler.handleCacheEvictError(testException, null, 1L));
        assertDoesNotThrow(() -> errorHandler.handleCacheClearError(testException, null));
    }

    @Test
    @DisplayName("Should create CacheManager and RedisTemplate beans")
    void shouldCreateCacheManagerAndRedisTemplate() {
        org.springframework.data.redis.connection.RedisConnectionFactory factory =
                mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);

        org.springframework.cache.CacheManager cacheManager = redisConfig.cacheManager(factory);
        assertNotNull(cacheManager);

        org.springframework.data.redis.core.RedisTemplate<String, Object> template = redisConfig.redisTemplate(factory);
        assertNotNull(template);
        assertNotNull(template.getKeySerializer());
        assertNotNull(template.getValueSerializer());

        org.springframework.data.redis.core.RedisTemplate<String, com.serjnn.ProductService.dtos.CacheableDiscountDto> discountTemplate =
                redisConfig.discountRedisTemplate(factory);
        assertNotNull(discountTemplate);
        assertNotNull(discountTemplate.getKeySerializer());
        assertNotNull(discountTemplate.getValueSerializer());
    }
}
