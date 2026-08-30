package com.serjnn.ProductService.services;

import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.exceptions.ProductNotFoundException;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.repo.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DiscountService discountService;

    @Mock
    private SubscriptionService subscriptionService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, discountService, subscriptionService);
    }

    @Test
    @DisplayName("Should find products by category and apply discounts")
    void shouldFindProductsByCategory() {
        Category category = Category.ELECTRONICS;
        Pageable pageable = PageRequest.of(0, 10);
        Product product = new Product(1L, "Phone", "Smartphone", new BigDecimal("500.00"), category);
        Product discounted = new Product(1L, "Phone", "Smartphone", new BigDecimal("450.00"), category);

        when(productRepository.findProductsByCategory(category, pageable))
                .thenReturn(new SliceImpl<>(List.of(product), pageable, false));
        when(discountService.applyDiscounts(List.of(product)))
                .thenReturn(List.of(discounted));

        Slice<Product> result = productService.findProductsByCategory(category, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(new BigDecimal("450.00"), result.getContent().get(0).price());
    }

    @Test
    @DisplayName("Should add product and return generated ID")
    void shouldAddProduct() {
        Product product = new Product(null, "Keyboard", "Mechanical", new BigDecimal("80.00"), Category.ELECTRONICS);
        when(productRepository.save(product)).thenReturn(10L);

        Long id = productService.add(product);

        assertEquals(10L, id);
        verify(productRepository, times(1)).save(product);
    }

    @Test
    @DisplayName("Should update existing product successfully")
    void shouldUpdateProduct() {
        Long id = 1L;
        Product updateData = new Product(null, "Updated Phone", "New desc", new BigDecimal("600.00"), Category.ELECTRONICS);
        when(productRepository.existsById(id)).thenReturn(true);
        when(productRepository.update(any(Product.class))).thenReturn(true);

        Product updated = productService.update(id, updateData);

        assertEquals(id, updated.id());
        assertEquals("Updated Phone", updated.name());
        verify(productRepository).update(any(Product.class));
    }

    @Test
    @DisplayName("Should throw ProductNotFoundException when updating non-existent product")
    void shouldThrowExceptionWhenUpdatingNonExistentProduct() {
        Long id = 999L;
        Product updateData = new Product(null, "Updated Phone", "New desc", new BigDecimal("600.00"), Category.ELECTRONICS);
        when(productRepository.existsById(id)).thenReturn(false);

        assertThrows(ProductNotFoundException.class, () -> productService.update(id, updateData));
        verify(productRepository, never()).update(any());
    }

    @Test
    @DisplayName("Should delete product successfully")
    void shouldDeleteProduct() {
        Long id = 1L;
        when(productRepository.existsById(id)).thenReturn(true);
        when(productRepository.deleteById(id)).thenReturn(true);

        assertDoesNotThrow(() -> productService.delete(id));
        verify(productRepository).deleteById(id);
    }

    @Test
    @DisplayName("Should throw ProductNotFoundException when deleting non-existent product")
    void shouldThrowExceptionWhenDeletingNonExistentProduct() {
        Long id = 999L;
        when(productRepository.existsById(id)).thenReturn(false);

        assertThrows(ProductNotFoundException.class, () -> productService.delete(id));
        verify(productRepository, never()).deleteById(id);
    }

    @Test
    @DisplayName("Should search products by keyword")
    void shouldSearchProducts() {
        String keyword = "phone";
        Pageable pageable = PageRequest.of(0, 10);
        Product product = new Product(1L, "iPhone", "Apple phone", new BigDecimal("999.00"), Category.ELECTRONICS);

        when(productRepository.searchByNameOrDescription(keyword, pageable))
                .thenReturn(new SliceImpl<>(List.of(product), pageable, false));
        when(discountService.applyDiscounts(List.of(product)))
                .thenReturn(List.of(product));

        Slice<Product> result = productService.search(keyword, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals("iPhone", result.getContent().get(0).name());
    }
}
