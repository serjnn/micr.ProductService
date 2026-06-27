package com.serjnn.ProductService.dto;

import com.serjnn.ProductService.model.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        if (product == null) {
            return null;
        }
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getCategory()
        );
    }

    public Product toEntity(ProductRequest request) {
        if (request == null) {
            return null;
        }
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.category());
        return product;
    }
}
