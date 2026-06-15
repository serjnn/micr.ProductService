package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.redis.DiscountCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountService {
    private final RestTemplate restTemplate;
    private final DiscountCacheManager discountCacheManager;

    @Value("${app.services.discount-url}")
    private String discountUrl;

    public List<Product> applyDiscounts(List<Product> products) {
        return products.stream().map(this::applyDiscount).collect(Collectors.toList());
    }

    public Product applyDiscount(Product product) {
        Optional<CacheableDiscountDto> discountOpt = getDiscountByAnyCost(product.id());
        if (discountOpt.isPresent() && discountOpt.get().discount() > 0) {
            BigDecimal discount = BigDecimal.valueOf(discountOpt.get().discount());

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
        return product;
    }

    public Optional<CacheableDiscountDto> getDiscountByAnyCost(Long id) {
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

    public void updateCache(CacheableDiscountDto cacheableDiscountDto) {
        discountCacheManager.addToCache(cacheableDiscountDto);
    }

    private Optional<CacheableDiscountDto> askDiscountService(Long productId) {
        try {
            CacheableDiscountDto response =
                    restTemplate.getForObject(discountUrl + productId, CacheableDiscountDto.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Error while fetching discount via rest for product {}: {}", productId,
                    e.getMessage());
            return Optional.empty();
        }
    }
}
