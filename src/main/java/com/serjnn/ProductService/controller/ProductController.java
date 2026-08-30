package com.serjnn.ProductService.controller;

import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.exceptions.ProductNotFoundException;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.services.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
@Tag(name = "Product Controller", description = "RESTful APIs for managing products and client discount subscriptions")
@Slf4j
@Validated
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get all products", description = "Retrieve a paginated slice of products with applied discounts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products successfully retrieved"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Slice<Product> all(Pageable pageable) {
        log.info("Received request to get all products");
        return productService.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Retrieve a specific product by ID with active discount price")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found and returned"),
            @ApiResponse(responseCode = "400", description = "Invalid product ID supplied"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Product getById(
            @Parameter(description = "Product ID", example = "1")
            @PathVariable("id") @Positive(message = "Product ID must be positive") Long id) {
        log.info("Received request to get product by ID: {}", id);
        return productService.getByIdWithDiscount(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @PostMapping("/by-ids")
    @Operation(summary = "Get products by multiple IDs", description = "Retrieve products matching a list of provided IDs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products successfully retrieved"),
            @ApiResponse(responseCode = "400", description = "Empty or invalid IDs list supplied"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Slice<Product> getProductsByIds(
            @Valid @RequestBody IdsRequest idsRequest,
            Pageable pageable) {
        log.info("Received request to get products by multiple IDs: {}", idsRequest.ids());
        return productService.findAllByIds(idsRequest.ids(), pageable);
    }

    @GetMapping("/category/{cat}")
    @Operation(summary = "Get products by category", description = "Retrieve products filtered by category")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products successfully retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid category value"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Slice<Product> bucket(
            @Parameter(description = "Product category", example = "ELECTRONICS")
            @PathVariable("cat") Category category,
            Pageable pageable) {
        log.info("Received request to get products by category: {}", category);
        return productService.findProductsByCategory(category, pageable);
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by keyword", description = "Search products by name or description keyword")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products search completed successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Slice<Product> search(
            @Parameter(description = "Search keyword", example = "phone")
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            Pageable pageable) {
        log.info("Received request to search products with keyword: {}", keyword);
        return productService.search(keyword, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new product", description = "Create and store a new product in the catalog")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid product input data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Long addProduct(@Valid @RequestBody Product product) {
        log.info("Received request to add a new product: {}", product.name());
        return productService.add(product);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing product", description = "Update the attributes of an existing product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid product data or ID"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Product updateProduct(
            @Parameter(description = "Product ID to update", example = "1")
            @PathVariable("id") @Positive(message = "Product ID must be positive") Long id,
            @Valid @RequestBody Product product) {
        log.info("Received request to update product ID: {}", id);
        return productService.update(id, product);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a product", description = "Remove a product from the catalog by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid product ID"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void deleteProduct(
            @Parameter(description = "Product ID to delete", example = "1")
            @PathVariable("id") @Positive(message = "Product ID must be positive") Long id) {
        log.info("Received request to delete product ID: {}", id);
        productService.delete(id);
    }

    @PostMapping("/{productId}/subscribe/{clientId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subscribe a client to a product's discount changes", description = "Subscribe a client to receive Kafka notifications when discounts change")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subscription created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid client or product ID"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "409", description = "Client is already subscribed to this product"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void subscribe(
            @Parameter(description = "Product ID", example = "1")
            @PathVariable("productId") @Positive(message = "Product ID must be positive") Long productId,
            @Parameter(description = "Client ID", example = "100")
            @PathVariable("clientId") @Positive(message = "Client ID must be positive") Long clientId) {
        log.info("Received subscription request: Client {} for Product {}", clientId, productId);
        productService.subscribe(clientId, productId);
    }

    @DeleteMapping("/{productId}/subscribe/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unsubscribe a client from a product's discount changes", description = "Cancel a client's subscription for discount notifications")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Unsubscribed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid client or product ID"),
            @ApiResponse(responseCode = "404", description = "Subscription not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void unsubscribe(
            @Parameter(description = "Product ID", example = "1")
            @PathVariable("productId") @Positive(message = "Product ID must be positive") Long productId,
            @Parameter(description = "Client ID", example = "100")
            @PathVariable("clientId") @Positive(message = "Client ID must be positive") Long clientId) {
        log.info("Received unsubscribe request: Client {} for Product {}", clientId, productId);
        productService.unsubscribe(clientId, productId);
    }

    @GetMapping("/{productId}/subscribers")
    @Operation(summary = "Get subscriber client IDs for a product", description = "Retrieve a paginated list of client IDs subscribed to a product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscribers retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid product ID"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Slice<Long> getProductSubscribers(
            @Parameter(description = "Product ID", example = "1")
            @PathVariable("productId") @Positive(message = "Product ID must be positive") Long productId,
            Pageable pageable) {
        log.info("Received request to get subscribers for product {}", productId);
        return productService.getSubscriberClientIds(productId, pageable);
    }

    @GetMapping("/client/{clientId}/subscriptions")
    @Operation(summary = "Get subscribed product IDs for a client", description = "Retrieve a paginated list of product IDs a client is subscribed to")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscriptions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid client ID"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Slice<Long> getClientSubscriptions(
            @Parameter(description = "Client ID", example = "100")
            @PathVariable("clientId") @Positive(message = "Client ID must be positive") Long clientId,
            Pageable pageable) {
        log.info("Received request to get subscriptions for client {}", clientId);
        return productService.getSubscribedProductIds(clientId, pageable);
    }
}
