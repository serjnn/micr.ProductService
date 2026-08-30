package com.serjnn.ProductService.redis;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
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

        discountCacheManager = new DiscountCacheManager(builder);

        Field field = DiscountCacheManager.class.getDeclaredField("discountUrl");
        field.setAccessible(true);
        field.set(discountCacheManager, "http://discount/api/v1/discounts/");
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
}
