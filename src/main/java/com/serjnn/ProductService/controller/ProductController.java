package com.serjnn.ProductService.controller;

import com.serjnn.ProductService.dto.ProductRequest;
import com.serjnn.ProductService.dto.ProductResponse;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
@Tag(name = "Product API", description = "Endpoints for managing products and client subscriptions")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get products", description = "Retrieve all products with optional filters for category and specific IDs")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved products list")
    public Flux<ProductResponse> getProducts(
            @RequestParam(value = "category", required = false) Category category,
            @RequestParam(value = "ids", required = false) List<Long> ids) {
        log.info("Request received: Get products. Category: {}, IDs: {}", category, ids);
        if (ids != null && !ids.isEmpty()) {
            return productService.findProductsByIds(ids);
        } else if (category != null) {
            return productService.findProductsByCategory(category);
        } else {
            return productService.findAll();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Retrieve details for a single product by its ID")
    @ApiResponse(responseCode = "200", description = "Product found")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public Mono<ResponseEntity<ProductResponse>> getProductById(@PathVariable("id") Long id) {
        log.info("Request received: Get product by ID: {}", id);
        return productService.findProductById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create product", description = "Add a new product to the catalog")
    @ApiResponse(responseCode = "201", description = "Product successfully created")
    @ApiResponse(responseCode = "400", description = "Invalid input request")
    public Mono<ProductResponse> addProduct(@Valid @RequestBody ProductRequest productRequest) {
        log.info("Request received: Add new product: {}", productRequest);
        return productService.add(productRequest);
    }

    @PostMapping("/{productId}/subscriptions/{clientId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subscribe to product updates", description = "Register a client to receive discount updates for a specific product")
    @ApiResponse(responseCode = "201", description = "Successfully subscribed")
    @ApiResponse(responseCode = "400", description = "Invalid product ID or client ID")
    public Mono<Void> subscribe(@PathVariable("productId") Long productId, @PathVariable("clientId") Long clientId) {
        log.info("Request received: Client {} subscribe to product {}", clientId, productId);
        return productService.subscribe(clientId, productId);
    }
}
