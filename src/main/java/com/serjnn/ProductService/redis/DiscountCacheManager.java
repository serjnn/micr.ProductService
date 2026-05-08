package com.serjnn.ProductService.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serjnn.ProductService.dtos.DiscountResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscountCacheManager {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String DISCOUNTS_HASH_KEY = "discounts_hash";

    public void addToCache(DiscountResponseDto discountResponseDto) {
        log.info("adding to cache " + discountResponseDto);
        redisTemplate.opsForHash().put(DISCOUNTS_HASH_KEY, String.valueOf(discountResponseDto.productId())
                , mapToJsonString(discountResponseDto));
    }

    @SneakyThrows
    private DiscountResponseDto readFromJsonString(Object value) {
        if (value == null) return null;
        return objectMapper.readValue(value.toString(), DiscountResponseDto.class);
    }

    @SneakyThrows
    private String mapToJsonString(DiscountResponseDto discountEntity) {
        return objectMapper.writeValueAsString(discountEntity);
    }

    public Optional<DiscountResponseDto> getDiscountByProductId(Long productId) {
        Object value = redisTemplate.opsForHash().get(DISCOUNTS_HASH_KEY, productId.toString());
        return Optional.ofNullable(readFromJsonString(value));
    }

    public void clearCache() {
        redisTemplate.delete(DISCOUNTS_HASH_KEY);
    }

    public void removeFromCache(Long productId) {
        log.info("Removing product {} from cache", productId);
        redisTemplate.opsForHash().delete(DISCOUNTS_HASH_KEY, productId.toString());
    }
}
