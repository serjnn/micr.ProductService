package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.redis.DiscountCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    @Mock
    private DiscountCacheManager discountCacheManager;

    private DiscountService discountService;

    @BeforeEach
    void setUp() {
        discountService = new DiscountService(discountCacheManager);
    }

    @Test
    @DisplayName("Should correctly apply discount with precision rounding")
    void shouldApplyDiscountCorrectly() {
        Long productId = 1L;
        Product product = new Product(productId, "Laptop", "High end", new BigDecimal("1000.00"), Category.ELECTRONICS);

        when(discountCacheManager.getDiscountByProductId(productId))
                .thenReturn(Optional.of(new CacheableDiscountDto(productId, 15.0)));

        Product discountedProduct = discountService.applyDiscount(product);

        assertNotNull(discountedProduct);
        assertEquals(new BigDecimal("850.00"), discountedProduct.price());
    }

    @Test
    @DisplayName("Should handle fractional discounts without ArithmeticException")
    void shouldHandleFractionalDiscountsWithoutException() {
        Long productId = 2L;
        Product product = new Product(productId, "Shirt", "Cotton", new BigDecimal("99.99"), Category.CLOTH);

        when(discountCacheManager.getDiscountByProductId(productId))
                .thenReturn(Optional.of(new CacheableDiscountDto(productId, 33.3333)));

        Product discountedProduct = discountService.applyDiscount(product);

        assertNotNull(discountedProduct);
        assertNotNull(discountedProduct.price());
        assertEquals(2, discountedProduct.price().scale());
    }

    @Test
    @DisplayName("Should return unchanged product when discount is zero or negative")
    void shouldReturnUnchangedProductWhenDiscountIsZeroOrNegative() {
        Long productId = 3L;
        Product product = new Product(productId, "Toy", "Action figure", new BigDecimal("50.00"), Category.TOYS);

        when(discountCacheManager.getDiscountByProductId(productId))
                .thenReturn(Optional.of(new CacheableDiscountDto(productId, 0.0)));

        Product result = discountService.applyDiscount(product);

        assertEquals(new BigDecimal("50.00"), result.price());
    }

    @Test
    @DisplayName("Should return unchanged product when discount is not found")
    void shouldReturnUnchangedProductWhenDiscountNotFound() {
        Long productId = 4L;
        Product product = new Product(productId, "Food Item", "Snack", new BigDecimal("10.00"), Category.FOOD);

        when(discountCacheManager.getDiscountByProductId(productId))
                .thenReturn(Optional.empty());

        Product result = discountService.applyDiscount(product);

        assertEquals(new BigDecimal("10.00"), result.price());
    }

    @Test
    @DisplayName("Should safely handle null product or null price")
    void shouldSafelyHandleNullProductOrPrice() {
        assertNull(discountService.applyDiscount(null));

        Product productWithNullPrice = new Product(5L, "Test", "Desc", null, Category.FOOD);
        Product result = discountService.applyDiscount(productWithNullPrice);
        assertNull(result.price());
    }

    @Test
    @DisplayName("Should apply discounts across list of products")
    void shouldApplyDiscountsToList() {
        Product p1 = new Product(1L, "P1", "D1", new BigDecimal("100.00"), Category.ELECTRONICS);
        Product p2 = new Product(2L, "P2", "D2", new BigDecimal("200.00"), Category.FOOD);

        when(discountCacheManager.getDiscountByProductId(1L))
                .thenReturn(Optional.of(new CacheableDiscountDto(1L, 10.0)));
        when(discountCacheManager.getDiscountByProductId(2L))
                .thenReturn(Optional.of(new CacheableDiscountDto(2L, 20.0)));

        List<Product> discountedList = discountService.applyDiscounts(List.of(p1, p2));

        assertEquals(2, discountedList.size());
        assertEquals(new BigDecimal("90.00"), discountedList.get(0).price());
        assertEquals(new BigDecimal("160.00"), discountedList.get(1).price());
    }
}
