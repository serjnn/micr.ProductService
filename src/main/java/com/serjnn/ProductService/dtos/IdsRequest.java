package com.serjnn.ProductService.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record IdsRequest(
        @NotEmpty(message = "Product IDs list must not be empty")
        List<@NotNull(message = "ID cannot be null") @Positive(message = "ID must be positive") Long> ids
) {
}
