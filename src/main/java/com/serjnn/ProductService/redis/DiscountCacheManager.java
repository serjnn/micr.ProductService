package com.serjnn.ProductService.redis;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Component
public class DiscountCacheManager {
    private RestClient restClient;

    @Value("${app.services.discount-url}")
    private String discountUrl;

    public DiscountCacheManager(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(value = "discounts", key = "#productId")
    @CircuitBreaker(name = "discountService", fallbackMethod = "fallbackGetDiscount")
    @Retry(name = "discountService")
    public Optional<CacheableDiscountDto> getDiscountByProductId(Long productId) {
        log.info("Cache miss for product {} discount. Fetching from external service.", productId);
        CacheableDiscountDto response = restClient.get()
                .uri(discountUrl + productId)
                .retrieve()
                .body(CacheableDiscountDto.class);

        if (response != null) {
            log.info("Fetched discount for product {}: {}", productId, response.discount());
            return Optional.of(response);
        }
        log.info("No discount information found for product {}. Caching default ( 0.0 ).", productId);
        return Optional.of(new CacheableDiscountDto(productId, 0.0));
    }

    public Optional<CacheableDiscountDto> fallbackGetDiscount(Long productId, Throwable t) {
        log.error("Fallback for product {} discount due to: {}", productId, t.getMessage());
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
