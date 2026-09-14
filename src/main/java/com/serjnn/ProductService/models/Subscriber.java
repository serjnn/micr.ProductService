package com.serjnn.ProductService.models;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record Subscriber(
        Long id,
        @NotNull @Positive Long productId,
        @NotNull @Positive Long clientId
) {}
