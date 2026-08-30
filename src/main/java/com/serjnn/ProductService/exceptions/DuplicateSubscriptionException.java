package com.serjnn.ProductService.exceptions;

public class DuplicateSubscriptionException extends ProductServiceException {
    private final Long productId;
    private final Long clientId;

    public DuplicateSubscriptionException(Long productId, Long clientId) {
        super(String.format("Client '%d' is already subscribed to product '%d'", clientId, productId));
        this.productId = productId;
        this.clientId = clientId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getClientId() {
        return clientId;
    }
}
