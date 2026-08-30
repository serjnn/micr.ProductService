package com.serjnn.ProductService.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;

public record DiscountChangesDto(
        @NotNull(message = "Product ID is required")
        @Positive(message = "Product ID must be positive")
        Long productId,
        @NotNull(message = "New discount is required")
        @DecimalMin(value = "0.0", message = "New discount cannot be negative")
        Double newDiscount,
        @DecimalMin(value = "0.0", message = "Previous discount cannot be negative")
        Double prevDiscount
) implements Serializable {
}
