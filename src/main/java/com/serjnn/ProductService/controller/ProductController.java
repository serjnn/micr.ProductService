package com.serjnn.ProductService.controller;

import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.services.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
@Tag(name = "Product Controller", description = "RESTful APIs for managing products and subscriptions")
@Slf4j
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get all products")
    public Slice<Product> all(Pageable pageable) {
        log.info("Received request to get all products");
        return productService.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public Product getById(@PathVariable Long id) {
        log.info("Received request to get product by ID: {}", id);
        return productService.getDiscountByAnyCost(id)
                .map(discount -> {
                    log.info("Found discount for product {}. Re-fetching with updated price.", id);
                    return productService.findAllByIds(List.of(id), PageRequest.of(0, 1)).getContent().get(0);
                }) // Helper to re-fetch with discount
                .orElseGet(() -> {
                    log.info("No discount found for product {}. Fetching original product.", id);
                    return productService.findById(id).orElseThrow();
                });
    }

    @PostMapping("/by-ids")
    @Operation(summary = "Get products by multiple IDs")
    public Slice<Product> getProductsByIds(@RequestBody IdsRequest idsRequest, Pageable pageable) {
        log.info("Received request to get products by multiple IDs: {}", idsRequest.ids());
        return productService.findAllByIds(idsRequest.ids(), pageable);
    }

    @GetMapping("/category/{cat}")
    @Operation(summary = "Get products by category")
    public Slice<Product> bucket(@PathVariable("cat") Category category, Pageable pageable) {
        log.info("Received request to get products by category: {}", category);
        return productService.findProductsByCategory(category, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new product")
    public void addProduct(@RequestBody Product product) {
        log.info("Received request to add a new product: {}", product.name());
        productService.add(product);
    }

    @PostMapping("/{productId}/subscribe/{clientId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subscribe a client to a product's discount changes")
    public void subscribe(@PathVariable("productId") Long productId, @PathVariable("clientId") Long clientId) {
        log.info("Received subscription request: Client {} for Product {}", clientId, productId);
        productService.subscribe(clientId, productId);
    }
}
