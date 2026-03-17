package com.serjnn.ProductService.controller;

import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.services.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
@Tag(name = "Product Controller", description = "RESTful APIs for managing products and subscriptions")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get all products")
    public List<Product> all() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public Product getById(@PathVariable Long id) {
        return productService.getDiscountByAnyCost(id)
                .map(discount -> productService.findAllByIds(List.of(id)).get(0)) // Helper to re-fetch with discount
                .orElseGet(() -> productService.findById(id).orElseThrow());
    }

    @PostMapping("/by-ids")
    @Operation(summary = "Get products by multiple IDs")
    public List<Product> getProductsByIds(@RequestBody IdsRequest idsRequest) {
        return productService.findAllByIds(idsRequest.ids());
    }

    @GetMapping("/category/{cat}")
    @Operation(summary = "Get products by category")
    public List<Product> bucket(@PathVariable("cat") Category category) {
        return productService.findProductsByCategory(category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new product")
    public void addProduct(@RequestBody Product product) {
        productService.add(product);
    }

    @PostMapping("/{productId}/subscribe/{clientId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subscribe a client to a product's discount changes")
    public void subscribe(@PathVariable("productId") Long productId, @PathVariable("clientId") Long clientId) {
        productService.subscribe(clientId, productId);
    }
}
