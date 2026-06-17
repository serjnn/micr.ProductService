package com.serjnn.ProductService.redis;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscountCacheManager {
    private final RestTemplate restTemplate;

    @Value("${app.services.discount-url}")
    private String discountUrl;

    @Cacheable(value = "discounts", key = "#productId")
    public Optional<CacheableDiscountDto> getDiscountByProductId(Long productId) {
        log.info("Cache miss for product {} discount. Fetching from external service.", productId);
        try {
            CacheableDiscountDto response =
                    restTemplate.getForObject(discountUrl + productId, CacheableDiscountDto.class);
            if (response != null) {
                log.info("Fetched discount for product {}: {}", productId, response.discount());
                return Optional.of(response);
            }
        } catch (Exception e) {
            log.error("Error while fetching discount via rest for product {}: {}", productId,
                    e.getMessage());
        }
        log.info("No discount information found for product {}. Caching default ( 0.0 ).", productId);
        return Optional.of(new CacheableDiscountDto(productId, 0.0));
    }

    @CachePut(value = "discounts", key = "#cacheableDiscountDto.productId()")
    public Optional<CacheableDiscountDto> addToCache(CacheableDiscountDto cacheableDiscountDto) {
        log.info("Adding to cache: {}", cacheableDiscountDto);
        return Optional.of(cacheableDiscountDto);
    }

    @CacheEvict(value = "discounts", allEntries = true)
    public void clearCache() {
        log.info("Clearing all discounts cache");
    }
}
