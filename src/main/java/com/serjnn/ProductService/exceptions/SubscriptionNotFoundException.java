package com.serjnn.ProductService.exceptions;

public class SubscriptionNotFoundException extends ProductServiceException {
    public SubscriptionNotFoundException(Long productId, Long clientId) {
        super(String.format("Subscription for client '%d' and product '%d' was not found", clientId, productId));
    }
}
