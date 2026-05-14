package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.DiscountResponseDto;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.redis.DiscountCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductsDiscountManager {

    private final DiscountCacheManager discountCacheManager;
    private final DiscountClient discountClient;

    public List<Product> fetchAndCount(List<Product> products) {
        if (products == null || products.isEmpty()) return Collections.emptyList();

        List<Long> productIds = products.stream()
                .map(Product::id)
                .collect(Collectors.toList());

        // 1. Try to get from cache
        Map<Long, DiscountResponseDto> discountsMap = new HashMap<>(discountCacheManager.getDiscountsByProductIds(productIds));
        
        // 2. Identify missing IDs
        List<Long> missingIds = productIds.stream()
                .filter(id -> !discountsMap.containsKey(id))
                .distinct()
                .collect(Collectors.toList());

        // 3. Fetch missing from external service
        if (!missingIds.isEmpty()) {
            log.info("Cache miss for {} products. Fetching from external service.", missingIds.size());
            List<DiscountResponseDto> fetchedDiscounts = discountClient.callDiscountServiceBatch(missingIds);
            
            // 4. Add to cache and update our map
            if (!fetchedDiscounts.isEmpty()) {
                discountCacheManager.addAllToCache(fetchedDiscounts);
                fetchedDiscounts.forEach(d -> discountsMap.put(d.productId(), d));
            }
            
            // 5. Handle IDs that still don't have discount info (cache default 0.0)
            List<Long> stillMissingIds = missingIds.stream()
                    .filter(id -> !discountsMap.containsKey(id))
                    .toList();
            
            if (!stillMissingIds.isEmpty()) {
                log.info("No discount info for {} products. Caching default 0.0.", stillMissingIds.size());
                List<DiscountResponseDto> defaults = stillMissingIds.stream()
                        .map(id -> new DiscountResponseDto(id, 0.0))
                        .collect(Collectors.toList());
                discountCacheManager.addAllToCache(defaults);
                defaults.forEach(d -> discountsMap.put(d.productId(), d));
            }
        }

        // 6. Apply discounts
        return products.stream()
                .map(product -> {
                    DiscountResponseDto discountDto = discountsMap.get(product.id());
                    if (discountDto != null && discountDto.discount() > 0) {
                        return countDiscountForProduct(product, BigDecimal.valueOf(discountDto.discount()));
                    }
                    return product;
                })
                .collect(Collectors.toList());
    }


    private Product countDiscountForProduct(Product product, BigDecimal discount) {
        BigDecimal newPrice = product.price()
                .multiply(
                        BigDecimal.ONE.subtract(
                                discount.divide(BigDecimal.valueOf(100))))
                .setScale(2, RoundingMode.HALF_UP);
        log.debug("Applied discount {}% to product {}. New price: {}", discount,
                product.id(), newPrice);
        return new Product(product.id(), product.name(), product.description(), newPrice,
                product.category());
    }
}
