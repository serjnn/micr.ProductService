package com.serjnn.ProductService.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serjnn.ProductService.config.AppServicesProperties;
import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Slf4j
@Component
public class DiscountCacheManager {
    private RestClient restClient;
    private final AppServicesProperties appServicesProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public DiscountCacheManager(RestClient.Builder restClientBuilder,
                                AppServicesProperties appServicesProperties,
                                @Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
                                @Autowired(required = false) ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.appServicesProperties = appServicesProperties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public DiscountCacheManager(RestClient.Builder restClientBuilder, AppServicesProperties appServicesProperties) {
        this(restClientBuilder, appServicesProperties, null, new ObjectMapper());
    }

    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(value = "discounts", key = "#productId")
    @CircuitBreaker(name = "discountService", fallbackMethod = "fallbackGetDiscount")
    @Retry(name = "discountService")
    public Optional<CacheableDiscountDto> getDiscountByProductId(Long productId) {
        log.info("Cache miss for product {} discount. Fetching from external service.", productId);
        return fetchFromExternalService(productId);
    }

    public Optional<CacheableDiscountDto> fetchFromExternalService(Long productId) {
        try {
            CacheableDiscountDto response = restClient.get()
                    .uri(appServicesProperties.discountUrl() + productId)
                    .retrieve()
                    .body(CacheableDiscountDto.class);

            if (response != null) {
                log.info("Fetched discount for product {}: {}", productId, response.discount());
                return Optional.of(response);
            }
            log.info("No discount information found for product {}. Caching default ( 0.0 ).", productId);
            return Optional.of(new CacheableDiscountDto(productId, 0.0));
        } catch (Exception e) {
            return fallbackGetDiscount(productId, e);
        }
    }

    public Optional<CacheableDiscountDto> fallbackGetDiscount(Long productId, Throwable t) {
        log.error("Fallback for product {} discount due to: {}", productId, t != null ? t.getMessage() : "unknown error");
        return Optional.of(new CacheableDiscountDto(productId, 0.0));
    }

    public Map<Long, CacheableDiscountDto> getDiscountsByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> uniqueIds = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, CacheableDiscountDto> result = new HashMap<>();
        List<Long> missingIds = new ArrayList<>();

        if (redisTemplate != null) {
            try {
                List<String> redisKeys = uniqueIds.stream()
                        .map(id -> "discounts::" + id)
                        .toList();

                List<Object> cachedValues = redisTemplate.opsForValue().multiGet(redisKeys);

                if (cachedValues != null && cachedValues.size() == uniqueIds.size()) {
                    for (int i = 0; i < uniqueIds.size(); i++) {
                        Long id = uniqueIds.get(i);
                        Object val = cachedValues.get(i);
                        if (val instanceof CacheableDiscountDto dto) {
                            result.put(id, dto);
                        } else if (val != null) {
                            try {
                                CacheableDiscountDto dto = objectMapper.convertValue(val, CacheableDiscountDto.class);
                                result.put(id, dto);
                            } catch (Exception e) {
                                missingIds.add(id);
                            }
                        } else {
                            missingIds.add(id);
                        }
                    }
                } else {
                    missingIds.addAll(uniqueIds);
                }
            } catch (Exception e) {
                log.warn("Redis batch MGET failed: {}. Falling back to individual fetching.", e.getMessage());
                missingIds.addAll(uniqueIds);
            }
        } else {
            missingIds.addAll(uniqueIds);
        }

        for (Long missingId : missingIds) {
            try {
                Optional<CacheableDiscountDto> fetched = fetchFromExternalService(missingId);
                fetched.ifPresent(dto -> {
                    result.put(missingId, dto);
                    addToCache(dto);
                });
            } catch (Exception e) {
                log.warn("Failed to fetch discount for missing product ID {}: {}", missingId, e.getMessage());
                result.put(missingId, new CacheableDiscountDto(missingId, 0.0));
            }
        }

        return result;
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
