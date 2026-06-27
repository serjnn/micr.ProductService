package com.serjnn.ProductService.model;

import com.serjnn.ProductService.enums.Category;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@NoArgsConstructor
@Table(name = "product")
public class Product {
    @Id
    private Long id;

    private String name;

    private String description;

    private BigDecimal price;

    private Category category;

}