package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.redis.DiscountCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountService {
    private final DiscountCacheManager discountCacheManager;

    public List<Product> applyDiscounts(List<Product> products) {
        return products.stream().map(this::applyDiscount).collect(Collectors.toList());
    }

    public Product applyDiscount(Product product) {
        if (product == null || product.price() == null) {
            return product;
        }
        Optional<CacheableDiscountDto> discountOpt = getDiscountByAnyCost(product.id());
        if (discountOpt.isPresent() && discountOpt.get().discount() != null && discountOpt.get().discount() > 0) {
            BigDecimal discount = BigDecimal.valueOf(discountOpt.get().discount());

            BigDecimal discountFactor = BigDecimal.ONE.subtract(
                    discount.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            BigDecimal newPrice = product.price()
                    .multiply(discountFactor)
                    .setScale(2, RoundingMode.HALF_UP);
            log.debug("Applied discount {}% to product {}. New price: {}", discount,
                    product.id(), newPrice);
            return new Product(product.id(), product.name(), product.description(), newPrice,
                    product.category());
        }
        return product;
    }

    public Optional<CacheableDiscountDto> getDiscountByAnyCost(Long id) {
        try {
            return discountCacheManager.getDiscountByProductId(id);
        } catch (Exception e) {
            log.warn("Failed to retrieve discount for product {}: {}. Proceeding with default (no discount).",
                    id, e.getMessage());
            return Optional.of(new CacheableDiscountDto(id, 0.0));
        }
    }

    public void updateCache(CacheableDiscountDto cacheableDiscountDto) {
        try {
            discountCacheManager.addToCache(cacheableDiscountDto);
        } catch (Exception e) {
            log.warn("Failed to update discount cache for product {}: {}",
                    cacheableDiscountDto != null ? cacheableDiscountDto.productId() : null, e.getMessage());
        }
    }
}
