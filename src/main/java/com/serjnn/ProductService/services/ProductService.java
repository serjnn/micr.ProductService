package com.serjnn.ProductService.services;

import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final DiscountService discountService;
    private final SubscriptionService subscriptionService;

    public Slice<Product> findProductsByCategory(Category category, Pageable pageable) {
        Slice<Product> productsSlice = productRepository.findProductsByCategory(category, pageable);
        List<Product> discountedProducts = discountService.applyDiscounts(productsSlice.getContent());
        return new SliceImpl<>(discountedProducts, pageable, productsSlice.hasNext());
    }

    public Slice<Product> findAll(Pageable pageable) {
        Slice<Product> productsSlice = productRepository.findAll(pageable);
        List<Product> discountedProducts = discountService.applyDiscounts(productsSlice.getContent());
        return new SliceImpl<>(discountedProducts, pageable, productsSlice.hasNext());
    }

    public Slice<Product> findAllByIds(List<Long> ids, Pageable pageable) {
        Slice<Product> productsSlice = productRepository.findAllById(ids, pageable);
        List<Product> discountedProducts = discountService.applyDiscounts(productsSlice.getContent());
        return new SliceImpl<>(discountedProducts, pageable, productsSlice.hasNext());
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public Optional<Product> getByIdWithDiscount(Long id) {
        return discountService.getDiscountByAnyCost(id)
                .map(discount -> {
                    log.info("Found discount for product {}. Fetching with updated price.", id);
                    return productRepository.findById(id)
                            .map(discountService::applyDiscount)
                            .orElse(null);
                })
                .or(() -> {
                    log.info("No discount found for product {}. Fetching original product.", id);
                    return productRepository.findById(id);
                });
    }

    public Long add(Product product) {
        return productRepository.save(product);
    }

    public void subscribe(Long clientId, Long productId) {
        subscriptionService.subscribe(clientId, productId);
    }
}
