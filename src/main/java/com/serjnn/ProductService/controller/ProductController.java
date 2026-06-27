package com.serjnn.ProductService.controller;

import com.serjnn.ProductService.dto.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.model.Product;
import com.serjnn.ProductService.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/all")
    public Flux<Product> getAllProducts() {
        log.info("Request received: Get all products");
        return productService.findAll();
    }

    @PostMapping("/all/by-ids")
    public Flux<Product> getProductsByIds(@RequestBody IdsRequest idsRequest) {
        log.info("Request received: Get products by IDs: {}", idsRequest.getIds());
        return productService.findProductsByIds(idsRequest);
    }

    @GetMapping("/by-cat/{cat}")
    public Flux<Product> getProductsByCategory(@PathVariable("cat") Category category) {
        log.info("Request received: Get products by category: {}", category);
        return productService.findProductsByCategory(category);
    }

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> addProduct(@RequestBody Product product) {
        log.info("Request received: Add new product: {}", product);
        return productService.add(product);
    }

    @PostMapping("/subscribe/{clientId}/{productId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> subscribe(@PathVariable("clientId") Long clientId, @PathVariable("productId") Long productId) {
        log.info("Request received: Client {} subscribe to product {}", clientId, productId);
        return productService.subscribe(clientId, productId);
    }
}
