package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.redis.DiscountCacheManager;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductsDiscountManager {

    private final RestTemplate restTemplate;
    private final DiscountCacheManager discountCacheManager;


    @Value("${app.services.discount-url}")
    private String discountUrl;

    public List<Product> fetchAndCount(List<Product> products) {
        return products.stream().map(product -> {
            Optional<CacheableDiscountDto> discountOpt = getDiscountByAnyCost(product.id());
            if (discountOpt.isPresent() && discountOpt.get().discount() > 0) {
                BigDecimal discount = BigDecimal.valueOf(discountOpt.get().discount());
                return countDiscountForProduct(product, discount);
            }
            return product;
        }).collect(Collectors.toList());
    }

    private  Product countDiscountForProduct(Product product, BigDecimal discount) {
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

    public Product fetchAndCountOne(Product product){
        Optional<CacheableDiscountDto> discountOpt = getDiscountByAnyCost(product.id());
        return discountOpt.map(cacheableDiscountDto -> countDiscountForProduct(product,
                BigDecimal.valueOf(cacheableDiscountDto.discount()))).orElse(product);
    }

    @Retry(name = "discountService", fallbackMethod = "getDiscountFallback")
    private Optional<CacheableDiscountDto> getDiscountByAnyCost(Long id) {
        log.debug("Fetching discount for product {} from cache", id);
        Optional<CacheableDiscountDto> cached = discountCacheManager.getDiscountByProductId(id);
        if (cached.isPresent()) {
            log.debug("Cache hit for product {} discount", id);
            return cached;
        }

        log.info("Cache miss for product {} discount. Fetching from external service.", id);
        Optional<CacheableDiscountDto> fetched = askDiscountService(id);
        if (fetched.isPresent()) {
            log.info("Fetched discount for product {}: {}", id, fetched.get().discount());
            discountCacheManager.addToCache(fetched.get());
            return fetched;
        } else {
            log.info("No discount information found for product {}. Caching default ( 0.0 ).", id);
            discountCacheManager.addToCache(new CacheableDiscountDto(id, 0.0));
            return Optional.empty();
        }
    }

    private Optional<CacheableDiscountDto> askDiscountService(Long productId) {
        CacheableDiscountDto response =
                restTemplate.getForObject(discountUrl + productId, CacheableDiscountDto.class);
        return Optional.ofNullable(response);
    }

}
