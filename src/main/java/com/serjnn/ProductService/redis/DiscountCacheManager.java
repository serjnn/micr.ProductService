package com.serjnn.ProductService.redis;

import com.serjnn.ProductService.dtos.DiscountResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscountCacheManager {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String DISCOUNTS_HASH_KEY = "discounts_hash";

    public void addToCache(DiscountResponseDto discountResponseDto) {
        log.info("Adding to cache: {}", discountResponseDto);
        redisTemplate.opsForHash().put(DISCOUNTS_HASH_KEY, String.valueOf(discountResponseDto.productId()), discountResponseDto);
    }

    public void addAllToCache(List<DiscountResponseDto> discounts) {
        if (discounts == null || discounts.isEmpty()) return;
        log.info("Adding {} discounts to cache", discounts.size());
        Map<String, DiscountResponseDto> map = discounts.stream()
                .collect(Collectors.toMap(
                        d -> String.valueOf(d.productId()),
                        d -> d
                ));
        redisTemplate.opsForHash().putAll(DISCOUNTS_HASH_KEY, map);
    }

    public Optional<DiscountResponseDto> getDiscountByProductId(Long productId) {
        DiscountResponseDto value = (DiscountResponseDto) redisTemplate.opsForHash().get(DISCOUNTS_HASH_KEY, String.valueOf(productId));
        return Optional.ofNullable(value);
    }

    public Map<Long, DiscountResponseDto> getDiscountsByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Collections.emptyMap();

        List<Object> keys = productIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());

        List<Object> values = redisTemplate.opsForHash().multiGet(DISCOUNTS_HASH_KEY, keys);

        Map<Long, DiscountResponseDto> result = new HashMap<>();
        for (int i = 0; i < productIds.size(); i++) {
            Object value = values.get(i);
            if (value instanceof DiscountResponseDto) {
                result.put(productIds.get(i), (DiscountResponseDto) value);
            }
        }
        return result;
    }

    public void clearCache() {
        redisTemplate.delete(DISCOUNTS_HASH_KEY);
    }

    public void removeFromCache(Long productId) {
        log.info("Removing product {} from cache", productId);
        redisTemplate.opsForHash().delete(DISCOUNTS_HASH_KEY, productId.toString());
    }
}
