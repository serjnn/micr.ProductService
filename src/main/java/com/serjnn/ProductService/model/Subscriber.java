package com.serjnn.ProductService.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "subscribers")
@Getter
@Setter

public class Subscriber {

    @Id
    private Long id;

    @Column("product_id")
    private Long productId;

    @Column("client_id")
    private Long clientId;

    public Subscriber(Long productId, Long clientId) {
        this.productId = productId;
        this.clientId = clientId;
    }
}
