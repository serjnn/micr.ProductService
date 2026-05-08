package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.DiscountResponseDto;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.models.Subscriber;
import com.serjnn.ProductService.repo.ProductRepository;
import com.serjnn.ProductService.repo.SubscribersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final SubscribersRepository subscribersRepository;
    private final ProductsDiscountManager productsDiscountManager;


    public Slice<Product> findProductsByCategory(Category category, Pageable pageable) {
        Slice<Product> productsSlice = productRepository.findProductsByCategory(category, pageable);
        List<Product> discountedProducts =
                productsDiscountManager.fetchAndCount(productsSlice.getContent());
        return new SliceImpl<>(discountedProducts, pageable, productsSlice.hasNext());
    }

    public Slice<Product> findAll(Pageable pageable) {
        Slice<Product> productsSlice = productRepository.findAll(pageable);
        List<Product> discountedProducts =
                productsDiscountManager.fetchAndCount(productsSlice.getContent());
        return new SliceImpl<>(discountedProducts, pageable, productsSlice.hasNext());
    }


    public Slice<Product> findAllByIdsSliced(List<Long> ids, Pageable pageable) {
        Slice<Product> productsSlice = productRepository.findProductsById(ids, pageable);
        List<Product> discountedProducts =
                productsDiscountManager.fetchAndCount(productsSlice.getContent());
        log.debug("Found {} discounted products", discountedProducts.size());
        return new SliceImpl<>(discountedProducts, pageable, productsSlice.hasNext());
    }

    public List<Product> findProductsByIds(List<Long> ids) {
        List<Product> products = productRepository.findProductsById(ids);

        List<Product> discountedProducts = productsDiscountManager.fetchAndCount(products);
        log.debug("Found {} discounted products (unpaginated)", discountedProducts.size());
        return discountedProducts;
    }

    public Product findById(Long id) {
        Product product =
                productRepository.findById(id).orElseThrow(() -> new NoSuchElementException(
                        "Product not found with id: " + id));
        return productsDiscountManager.fetchAndCount(List.of(product)).get(0);
    }

    public Long add(Product product) {
        return productRepository.save(product);
    }

    public void subscribe(Long clientId, Long productId) {
        log.info("Subscribing client {} to product {}", clientId, productId);
        subscribersRepository.save(new Subscriber(null, productId, clientId));
    }


}
