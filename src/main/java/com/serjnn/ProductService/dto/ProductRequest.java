package com.serjnn.ProductService.dto;

import com.serjnn.ProductService.enums.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Request payload for creating a product")
public record ProductRequest(
    @Schema(description = "Name of the product", example = "Smart TV")
    @NotBlank(message = "Product name must not be blank")
    String name,

    @Schema(description = "Description of the product", example = "55-inch 4K UHD Smart OLED TV")
    String description,

    @Schema(description = "Price of the product (before discount)", example = "1200.00")
    @NotNull(message = "Price must not be null")
    @Positive(message = "Price must be positive")
    BigDecimal price,

    @Schema(description = "Product category", example = "ELECTRONICS")
    @NotNull(message = "Category must not be null")
    Category category
) {}
