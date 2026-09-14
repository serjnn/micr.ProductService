package com.serjnn.ProductService.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;

public record DiscountChangesDto(
        @NotNull
        @Positive
        Long productId,
        @NotNull
        @DecimalMin("0.0")
        Double newDiscount,
        @DecimalMin("0.0")
        Double prevDiscount
) implements Serializable {}
