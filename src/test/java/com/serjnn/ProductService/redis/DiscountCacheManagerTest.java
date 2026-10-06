package com.serjnn.ProductService.redis;

import com.serjnn.ProductService.config.AppServicesProperties;
import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountCacheManagerTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private DiscountCacheManager discountCacheManager;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.build()).thenReturn(restClient);

        AppServicesProperties appServicesProperties = new AppServicesProperties("http://discount/api/v1/discounts/");
        discountCacheManager = new DiscountCacheManager(builder, appServicesProperties);
    }

    @Test
    @DisplayName("Should fetch and return discount from external service")
    void shouldFetchDiscountSuccessfully() {
        Long productId = 1L;
        CacheableDiscountDto expectedDto = new CacheableDiscountDto(productId, 15.0);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(CacheableDiscountDto.class)).thenReturn(expectedDto);

        Optional<CacheableDiscountDto> result = discountCacheManager.getDiscountByProductId(productId);

        assertTrue(result.isPresent());
        assertEquals(15.0, result.get().discount());
        assertEquals(productId, result.get().productId());
    }

    @Test
    @DisplayName("Should return 0.0 default discount when external service returns null")
    void shouldReturnDefaultWhenBodyIsNull() {
        Long productId = 2L;

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(CacheableDiscountDto.class)).thenReturn(null);

        Optional<CacheableDiscountDto> result = discountCacheManager.getDiscountByProductId(productId);

        assertTrue(result.isPresent());
        assertEquals(0.0, result.get().discount());
        assertEquals(productId, result.get().productId());
    }

    @Test
    @DisplayName("Should return fallback 0.0 discount on failure")
    void shouldReturnFallbackOnException() {
        Long productId = 3L;
        RuntimeException ex = new RuntimeException("Service down");

        Optional<CacheableDiscountDto> result = discountCacheManager.fallbackGetDiscount(productId, ex);

        assertTrue(result.isPresent());
        assertEquals(0.0, result.get().discount());
        assertEquals(productId, result.get().productId());
    }

    @Test
    @DisplayName("Should return cached item on addToCache")
    void shouldAddToCache() {
        CacheableDiscountDto dto = new CacheableDiscountDto(4L, 25.0);
        Optional<CacheableDiscountDto> result = discountCacheManager.addToCache(dto);

        assertTrue(result.isPresent());
        assertEquals(dto, result.get());
    }

    @Test
    @DisplayName("Should batch retrieve discounts from Redis when all keys exist")
    void shouldBatchRetrieveFromRedisWhenKeysExist() {
        org.springframework.data.redis.core.RedisTemplate<String, CacheableDiscountDto> mockRedisTemplate =
                mock(org.springframework.data.redis.core.RedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, CacheableDiscountDto> mockValueOps =
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);

        CacheableDiscountDto dto1 = new CacheableDiscountDto(10L, 10.0);
        CacheableDiscountDto dto2 = new CacheableDiscountDto(20L, 20.0);

        when(mockValueOps.multiGet(List.of("discounts::10", "discounts::20")))
                .thenReturn(List.of(dto1, dto2));

        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.build()).thenReturn(restClient);
        AppServicesProperties appServicesProperties = new AppServicesProperties("http://discount/api/v1/discounts/");

        DiscountCacheManager manager = new DiscountCacheManager(builder, appServicesProperties, mockRedisTemplate);

        java.util.Map<Long, CacheableDiscountDto> result = manager.getDiscountsByProductIds(List.of(10L, 20L));

        assertEquals(2, result.size());
        assertEquals(10.0, result.get(10L).discount());
        assertEquals(20.0, result.get(20L).discount());
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("Should fetch missing discounts from external service during batch retrieval")
    void shouldFetchMissingDiscountsFromExternalServiceInBatch() {
        org.springframework.data.redis.core.RedisTemplate<String, CacheableDiscountDto> mockRedisTemplate =
                mock(org.springframework.data.redis.core.RedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, CacheableDiscountDto> mockValueOps =
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);

        CacheableDiscountDto dto1 = new CacheableDiscountDto(10L, 10.0);
        CacheableDiscountDto dto2 = new CacheableDiscountDto(20L, 30.0);

        // Key 10 is in cache, key 20 is a cache miss (null in Redis)
        when(mockValueOps.multiGet(List.of("discounts::10", "discounts::20")))
                .thenReturn(java.util.Arrays.asList(dto1, null));

        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.build()).thenReturn(restClient);
        AppServicesProperties appServicesProperties = new AppServicesProperties("http://discount/api/v1/discounts/");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("http://discount/api/v1/discounts/20")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(CacheableDiscountDto.class)).thenReturn(dto2);

        DiscountCacheManager manager = new DiscountCacheManager(builder, appServicesProperties, mockRedisTemplate);

        java.util.Map<Long, CacheableDiscountDto> result = manager.getDiscountsByProductIds(List.of(10L, 20L));

        assertEquals(2, result.size());
        assertEquals(10.0, result.get(10L).discount());
        assertEquals(30.0, result.get(20L).discount());
    }

    @Test
    @DisplayName("Should clear cache without throwing exception")
    void shouldClearCache() {
        assertDoesNotThrow(() -> discountCacheManager.clearCache());
    }
}
