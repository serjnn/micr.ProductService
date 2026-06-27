package com.serjnn.ProductService.repository;

import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.model.Product;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ProductRepository extends ReactiveCrudRepository<Product, Long> {
    Flux<Product> findProductsByCategory(Category category);

    Flux<Product> findAllById(Iterable<Long> ids);



}