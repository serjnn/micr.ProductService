package com.serjnn.ProductService.services;

import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.exceptions.ProductNotFoundException;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
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

    public Slice<Product> search(String keyword, Pageable pageable) {
        Slice<Product> productsSlice = productRepository.searchByNameOrDescription(keyword, pageable);
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

    @Transactional
    public Long add(Product product) {
        log.info("Adding new product: {}", product.name());
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long id, Product product) {
        log.info("Updating product with ID: {}", id);
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        Product updated = new Product(id, product.name(), product.description(), product.price(), product.category());
        productRepository.update(updated);
        return updated;
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting product with ID: {}", id);
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }

    @Transactional
    public void subscribe(Long clientId, Long productId) {
        subscriptionService.subscribe(clientId, productId);
    }

    @Transactional
    public void unsubscribe(Long clientId, Long productId) {
        subscriptionService.unsubscribe(clientId, productId);
    }

    public Slice<Long> getSubscribedProductIds(Long clientId, Pageable pageable) {
        return subscriptionService.getSubscribedProductIds(clientId, pageable);
    }

    public Slice<Long> getSubscriberClientIds(Long productId, Pageable pageable) {
        return subscriptionService.getSubscriberClientIds(productId, pageable);
    }
}
