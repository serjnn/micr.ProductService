package com.serjnn.ProductService.repo;

import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DataClassRowMapper<Product> rowMapper = new DataClassRowMapper<>(Product.class);

    public Slice<Product> findAll(Pageable pageable) {
        int pageSize = pageable.getPageSize();
        String sql = "SELECT * FROM product LIMIT ? OFFSET ?";
        List<Product> products = jdbcTemplate.query(sql, rowMapper, pageSize + 1, pageable.getOffset());
        
        boolean hasNext = products.size() > pageSize;
        if (hasNext) {
            products.remove(pageSize);
        }
        return new SliceImpl<>(products, pageable, hasNext);
    }

    public Slice<Product> findProductsByCategory(Category category, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        String sql = "SELECT * FROM product WHERE category = ? LIMIT ? OFFSET ?";
        List<Product> products = jdbcTemplate.query(sql, rowMapper, category.name(), pageSize + 1, pageable.getOffset());
        
        boolean hasNext = products.size() > pageSize;
        if (hasNext) {
            products.remove(pageSize);
        }
        return new SliceImpl<>(products, pageable, hasNext);
    }

    public Slice<Product> findProductsById(Iterable<Long> ids, Pageable pageable) {
        List<Long> idList = (List<Long>) ids;
        if (idList.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }
        int pageSize = pageable.getPageSize();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("ids", idList);
        parameters.addValue("limit", pageSize + 1);
        parameters.addValue("offset", pageable.getOffset());
        
        String sql = "SELECT * FROM product WHERE id IN (:ids) LIMIT :limit OFFSET :offset";
        List<Product> products = namedParameterJdbcTemplate.query(sql, parameters, rowMapper);
        
        boolean hasNext = products.size() > pageSize;
        if (hasNext) {
            products.remove(pageSize);
        }
        
        return new SliceImpl<>(products, pageable, hasNext);
    }

    public List<Product> findProductsById(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("ids", ids);
        String sql = "SELECT * FROM product WHERE id IN (:ids)";
        return namedParameterJdbcTemplate.query(sql, parameters, rowMapper);
    }

    public Optional<Product> findById(Long id) {
        List<Product> results = jdbcTemplate.query("SELECT * FROM product WHERE id = ?", rowMapper, id);
        return results.stream().findFirst();
    }

    public Long save(Product product) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO product (name, description, price, category) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, product.name());
            ps.setString(2, product.description());
            ps.setBigDecimal(3, product.price());
            ps.setString(4, product.category().name());
            return ps;
        }, keyHolder);

        Number key = (Number) keyHolder.getKeys().get("id");
        return key != null ? key.longValue() : null;
    }
}
