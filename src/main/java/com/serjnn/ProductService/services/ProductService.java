package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.models.Subscriber;
import com.serjnn.ProductService.redis.DiscountCacheManager;
import com.serjnn.ProductService.repo.ProductRepository;
import com.serjnn.ProductService.repo.SubscribersRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Pageable;
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
public class ProductService {
    private final ProductRepository productRepository;
    private final RestTemplate restTemplate;
    private final SubscribersRepository subscribersRepository;
    private final DiscountCacheManager discountCacheManager;

    @Autowired
    @Lazy
    private ProductService self;

    @Value("${app.services.discount-url}")
    private String discountUrl;

    public Slice<Product> findProductsByCategory(Category category, Pageable pageable) {
        Slice<Product> productsSlice = productRepository.findProductsByCategory(category, pageable);
        List<Product> discountedProducts = countDiscount(productsSlice.getContent());
        return new SliceImpl<>(discountedProducts, pageable, productsSlice.hasNext());
    }

    public Slice<Product> findAll(Pageable pageable) {
        Slice<Product> productsSlice = productRepository.findAll(pageable);
        List<Product> discountedProducts = countDiscount(productsSlice.getContent());
        return new SliceImpl<>(discountedProducts, pageable, productsSlice.hasNext());
    }


    public Slice<Product> findAllByIds(List<Long> ids, Pageable pageable) {
        Slice<Product> productsSlice = productRepository.findAllById(ids, pageable);
        List<Product> discountedProducts = countDiscount(productsSlice.getContent());
        return new SliceImpl<>(discountedProducts, pageable, productsSlice.hasNext());
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    private List<Product> countDiscount(List<Product> products) {
        return products.stream().map(product -> {
            Optional<CacheableDiscountDto> discountOpt = self.getDiscountByAnyCost(product.id());
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
        }).collect(Collectors.toList());
    }

    @Retry(name = "discountService", fallbackMethod = "getDiscountFallback")
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

    public Optional<CacheableDiscountDto> getDiscountFallback(Long id, Exception e) {
        log.error("Error fetching discount for product {} after retries: {}. Using fallback (0.0 discount).", 
                id, e.getMessage());
        return Optional.of(new CacheableDiscountDto(id, 0.0));
    }

    private Optional<CacheableDiscountDto> askDiscountService(Long productId) {
        CacheableDiscountDto response =
                restTemplate.getForObject(discountUrl + productId, CacheableDiscountDto.class);
        return Optional.ofNullable(response);
    }

    public Long add(Product product) {
        return productRepository.save(product);
    }

    public void subscribe(Long clientId, Long productId) {
        log.info("Subscribing client {} to product {}", clientId, productId);
        subscribersRepository.save(new Subscriber(null, productId, clientId));
    }
}
