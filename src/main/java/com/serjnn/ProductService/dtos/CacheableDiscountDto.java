package com.serjnn.ProductService.dtos;

import java.io.Serializable;

public record CacheableDiscountDto(Long productId, Double discount) implements Serializable {
}
