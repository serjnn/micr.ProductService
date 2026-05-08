package com.serjnn.ProductService.controller;

import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.services.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
        log.info("Received request to get one product by ID: {}", id);
        return productService.findById(id);

    }

    @PostMapping("/by-ids")
    @Operation(summary = "Get products by multiple IDs")
    public List<Product> getProductsByIds(@RequestBody IdsRequest idsRequest) {
        log.info("Received request to get products by multiple IDs: {}", idsRequest.ids());
        return productService.findProductsByIds(idsRequest.ids());
    }

    @GetMapping("/category/{cat}")
    @Operation(summary = "Get products by category")
    public Slice<Product> getProductsByCategory(@PathVariable("cat") Category category, Pageable pageable) {
        log.info("Received request to get products by category: {}", category);
        return productService.findProductsByCategory(category, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new product")
    public Long addProduct(@RequestBody Product product) {
        log.info("Received request to add a new product: {}", product.name());
        return productService.add(product);
    }

    @PostMapping("/{productId}/subscribe/{clientId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subscribe a client to a product's discount changes")
    public void subscribe(@PathVariable("productId") Long productId, @PathVariable("clientId") Long clientId) {
        log.info("Received subscription request: Client {} for Product {}", clientId, productId);
        productService.subscribe(clientId, productId);
    }
}
