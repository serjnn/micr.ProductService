package com.serjnn.ProductService.dtos;

import java.io.Serializable;

public record DiscountChangesDto(Long productId, Double newDiscount, Double prevDiscount) implements Serializable {
}
