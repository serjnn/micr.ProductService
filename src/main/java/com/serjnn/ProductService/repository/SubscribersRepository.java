package com.serjnn.ProductService.repository;

import com.serjnn.ProductService.models.Subscriber;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface SubscribersRepository extends ReactiveCrudRepository<Subscriber, Long> {

    @Query("SELECT client_id FROM subscribers WHERE product_id = :productId")
    Flux<Long> findClientIdsByProductId(Long productId);



}
