package com.serjnn.ProductService.exceptions;

public class ProductNotFoundException extends ProductServiceException {
    private final Long productId;

    public ProductNotFoundException(Long productId) {
        super(String.format("Product with ID '%d' was not found", productId));
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
