package com.serjnn.ProductService.dto;

import com.serjnn.ProductService.enums.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Product details response object")
public record ProductResponse(
    @Schema(description = "Unique ID of the product", example = "1")
    Long id,

    @Schema(description = "Name of the product", example = "Smart TV")
    String name,

    @Schema(description = "Description of the product", example = "55-inch 4K UHD Smart OLED TV")
    String description,

    @Schema(description = "Price of the product (discounted if discounts are available)", example = "1080.00")
    BigDecimal price,

    @Schema(description = "Product category", example = "ELECTRONICS")
    Category category
) {}
