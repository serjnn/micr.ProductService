package com.serjnn.ProductService.dtos;

import java.io.Serializable;

public record DiscountResponseDto(Long productId, Double discount) implements Serializable {
}
