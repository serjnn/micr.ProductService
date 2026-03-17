package com.serjnn.ProductService.controller;

import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.services.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping()
    public List<Product> all() {
        return productService.findAll();
    }

    @PostMapping("/by-ids")
    public List<Product> getProductsByIds(@RequestBody IdsRequest idsRequest) {
        return productService.findProductsByIds(idsRequest);
    }

    @GetMapping("/by-cat/{cat}")
    public List<Product> bucket(@PathVariable("cat") Category category) {
        return productService.findProductsByCategory(category);
    }

    @PostMapping("/add")
    public void addProduct(@RequestBody Product product) {
        productService.add(product);
    }

    @GetMapping("/subscribe/{clientId}/{productId}")
    public void subscribe(@PathVariable("clientId") Long clientId, @PathVariable("productId") Long productId) {
        productService.subscribe(clientId, productId);
    }
}
