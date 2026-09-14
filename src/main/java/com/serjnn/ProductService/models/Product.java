package com.serjnn.ProductService.models;

import com.serjnn.ProductService.enums.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record Product(
        Long id,
        @NotBlank
        @Size(max = 255)
        String name,
        @Size(max = 2000)
        String description,
        @NotNull
        @DecimalMin("0.01")
        BigDecimal price,
        @NotNull
        Category category
) {}
