package com.serjnn.ProductService.service;

import com.serjnn.ProductService.dto.DiscountDto;
import com.serjnn.ProductService.dto.ProductMapper;
import com.serjnn.ProductService.dto.ProductRequest;
import com.serjnn.ProductService.dto.ProductResponse;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.model.Product;
import com.serjnn.ProductService.model.Subscriber;
import com.serjnn.ProductService.repository.ProductRepository;
import com.serjnn.ProductService.repository.SubscribersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final WebClient webClient;
    private final SubscribersRepository subscribersRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository,
                          WebClient.Builder webClientBuilder,
                          SubscribersRepository subscribersRepository,
                          ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.webClient = webClientBuilder.build();
        this.subscribersRepository = subscribersRepository;
        this.productMapper = productMapper;
    }

    public Flux<ProductResponse> findProductsByCategory(Category category) {
        Flux<Product> products = productRepository.findProductsByCategory(category);
        return countDiscount(products).map(productMapper::toResponse);
    }

    public Flux<ProductResponse> findAll() {
        Flux<Product> products = productRepository.findAll();
        return countDiscount(products).map(productMapper::toResponse);
    }

    public Flux<ProductResponse> findProductsByIds(List<Long> ids) {
        Flux<Product> products = productRepository.findAllById(ids);
        return countDiscount(products).map(productMapper::toResponse);
    }

    public Mono<ProductResponse> findProductById(Long id) {
        return productRepository.findById(id)
                .flatMap(product -> countDiscount(Flux.just(product)).next())
                .map(productMapper::toResponse);
    }

    private Flux<Product> countDiscount(Flux<Product> products) {
        return webClient.get()
                .uri("lb://discount/api/v1/all")
                .retrieve()
                .bodyToFlux(DiscountDto.class)
                .timeout(Duration.ofSeconds(2))
                .collectMap(DiscountDto::getProductId, DiscountDto::getDiscount)
                .onErrorResume(e -> {
                    log.error("Failed to retrieve discounts from discount service, defaulting to no discounts", e);
                    return Mono.just(Collections.emptyMap());
                })
                .flatMapMany(discountsMap ->
                        products.map(product -> {
                            Double discount = discountsMap.get(product.getId());
                            if (discount != null && product.getPrice() != null) {
                                BigDecimal originalPrice = product.getPrice();
                                BigDecimal discountAmount = originalPrice.multiply(BigDecimal.valueOf(discount))
                                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                                product.setPrice(originalPrice.subtract(discountAmount));
                            }
                            return product;
                        })
                );
    }

    public Mono<ProductResponse> add(ProductRequest productRequest) {
        Product product = productMapper.toEntity(productRequest);
        return productRepository.save(product).map(productMapper::toResponse);
    }

    public Mono<Void> subscribe(Long clientId, Long productId) {
        log.info("Client {} requested subscription to product {}", clientId, productId);
        return productRepository.existsById(productId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new IllegalArgumentException("Product with ID " + productId + " does not exist"));
                    }
                    return subscribersRepository.save(new Subscriber(productId, clientId));
                })
                .then();
    }
}