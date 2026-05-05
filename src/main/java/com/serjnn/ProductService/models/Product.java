package com.serjnn.ProductService.models;

import com.serjnn.ProductService.enums.Category;
import java.math.BigDecimal;

public record Product(Long id, String name, String description, BigDecimal price,
                      Category category) {
}
