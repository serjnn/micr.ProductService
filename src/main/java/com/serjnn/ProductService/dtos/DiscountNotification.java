package com.serjnn.ProductService.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;

public record DiscountNotification(
        @NotNull @Positive Long productId,
        @NotNull @Positive Long clientId,
        @NotNull @DecimalMin("0.0") Double discount
) implements Serializable {
}
