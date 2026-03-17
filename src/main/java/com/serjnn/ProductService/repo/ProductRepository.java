package com.serjnn.ProductService.repo;

import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DataClassRowMapper<Product> rowMapper = new DataClassRowMapper<>(Product.class);

    public List<Product> findAll() {
        return jdbcTemplate.query("SELECT * FROM product", rowMapper);
    }

    public List<Product> findProductsByCategory(Category category) {
        return jdbcTemplate.query("SELECT * FROM product WHERE category = ?", rowMapper, category.name());
    }

    public List<Product> findAllById(Iterable<Long> ids) {
        List<Long> idList = (List<Long>) ids;
        if (idList.isEmpty()) {
            return Collections.emptyList();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("ids", idList);
        return namedParameterJdbcTemplate.query("SELECT * FROM product WHERE id IN (:ids)", parameters, rowMapper);
    }

    public Optional<Product> findById(Long id) {
        List<Product> results = jdbcTemplate.query("SELECT * FROM product WHERE id = ?", rowMapper, id);
        return results.stream().findFirst();
    }

    public void save(Product product) {
        jdbcTemplate.update("INSERT INTO product (name, description, price, category) VALUES (?, ?, ?, ?)",
                product.name(), product.description(), product.price(), product.category().name());
    }
}
