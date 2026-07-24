package com.serjnn.ProductService.dto;

public record DiscountNotification(Long productId, Long clientId, Double discount) {
}
