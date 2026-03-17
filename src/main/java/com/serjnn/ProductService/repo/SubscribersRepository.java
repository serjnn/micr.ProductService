package com.serjnn.ProductService.repo;

import com.serjnn.ProductService.models.Subscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SubscribersRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DataClassRowMapper<Subscriber> rowMapper = new DataClassRowMapper<>(Subscriber.class);

    public List<Long> findClientIdsByProductId(Long productId) {
        return jdbcTemplate.queryForList("SELECT client_id FROM subscribers WHERE product_id = ?", Long.class, productId);
    }

    public void save(Subscriber subscriber) {
        jdbcTemplate.update("INSERT INTO subscribers (product_id, client_id) VALUES (?, ?)",
                subscriber.productId(), subscriber.clientId());
    }
}
