package com.serjnn.ProductService.models;

import com.serjnn.ProductService.enums.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record Product(
        Long id,
        @NotBlank(message = "Product name must not be blank")
        @Size(max = 255, message = "Product name must not exceed 255 characters")
        String name,
        @Size(max = 2000, message = "Product description must not exceed 2000 characters")
        String description,
        @NotNull(message = "Product price is required")
        @DecimalMin(value = "0.01", message = "Product price must be greater than 0")
        BigDecimal price,
        @NotNull(message = "Product category is required")
        Category category
) {
}
