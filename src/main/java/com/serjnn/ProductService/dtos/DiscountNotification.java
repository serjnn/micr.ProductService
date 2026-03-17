package com.serjnn.ProductService.dtos;

public record DiscountNotification(Long productId, Long clientId, Double discount) {
}
