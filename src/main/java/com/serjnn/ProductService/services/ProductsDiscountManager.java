package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.DiscountResponseDto;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.redis.DiscountCacheManager;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductsDiscountManager {

    private final DiscountCacheManager discountCacheManager;
    private final DiscountClient discountClient;


    public List<Product> fetchAndCount(List<Product> products) {
        return products.stream()
                .map(product -> {
                    Optional<DiscountResponseDto> discountOpt = getDiscountByAnyCost(product.id());
                    if (discountOpt.isPresent() && discountOpt.get()
                            .discount() > 0) {
                        BigDecimal discount = BigDecimal.valueOf(discountOpt.get()
                                .discount());
                        return countDiscountForProduct(product, discount);
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


    private Optional<DiscountResponseDto> getDiscountByAnyCost(Long id) {
        log.debug("Trying to get discount {} from cache", id);
        Optional<DiscountResponseDto> cached = discountCacheManager.getDiscountByProductId(id);
        if (cached.isPresent()) {
            log.debug("Cache hit for product {} discount", id);
            return cached;
        }

        log.info("Cache miss for product {} discount. Fetching from external service.", id);
        Optional<DiscountResponseDto> fetched = discountClient.callDiscountService(id);
        if (fetched.isPresent()) {
            log.info("Fetched discount for product {}: {}",
                    id,
                    fetched.get()
                            .discount());
            discountCacheManager.addToCache(fetched.get());
            return fetched;
        } else {
            log.info("No discount information found for product {}. Caching default ( 0.0 ).", id);
            discountCacheManager.addToCache(new DiscountResponseDto(id, 0.0));
            return Optional.empty();
        }
    }



}
