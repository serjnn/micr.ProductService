package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.DiscountDto;
import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.models.Subscriber;
import com.serjnn.ProductService.repo.ProductRepository;
import com.serjnn.ProductService.repo.SubscribersRepository;
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

    public ProductService(ProductRepository productRepository,
                          WebClient.Builder webClientBuilder,
                          SubscribersRepository subscribersRepository) {
        this.productRepository = productRepository;
        this.webClient = webClientBuilder.build();
        this.subscribersRepository = subscribersRepository;
    }

    public Flux<Product> findProductsByCategory(Category category) {
        Flux<Product> products = productRepository.findProductsByCategory(category);
        return countDiscount(products);
    }

    public Flux<Product> findAll() {
        Flux<Product> products = productRepository.findAll();
        return countDiscount(products);
    }

    public Flux<Product> findProductsByIds(IdsRequest idsRequest) {
        List<Long> ids = idsRequest.getIds();
        Flux<Product> products = productRepository.findAllById(ids);
        return countDiscount(products);
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

    public Mono<Void> add(Product product) {
        return productRepository.save(product).then();

    }

    public Mono<Void> subscribe(Long clientId, Long productId) {
        return subscribersRepository.save(new Subscriber(productId, clientId)).then();
    }


}