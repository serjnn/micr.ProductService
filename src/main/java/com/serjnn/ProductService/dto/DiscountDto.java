package com.serjnn.ProductService.dto;


import lombok.*;

import java.io.Serializable;

@Getter
@ToString
public class DiscountDto implements Serializable {
    private Long productId;
    private Double discount;
}
