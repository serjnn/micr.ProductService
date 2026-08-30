package com.serjnn.ProductService.repo;

import com.serjnn.ProductService.models.Subscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SubscribersRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DataClassRowMapper<Subscriber> rowMapper = new DataClassRowMapper<>(Subscriber.class);

    public Slice<Long> findClientIdsByProductId(Long productId, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        String sql = "SELECT client_id FROM subscribers WHERE product_id = ? LIMIT ? OFFSET ?";
        List<Long> clientIds = jdbcTemplate.queryForList(sql, Long.class, productId, pageSize + 1, pageable.getOffset());
        
        // Convert to mutable list if it's not
        List<Long> resultList = new ArrayList<>(clientIds);
        
        boolean hasNext = resultList.size() > pageSize;
        if (hasNext) {
            resultList.remove(pageSize);
        }
        return new SliceImpl<>(resultList, pageable, hasNext);
    }

    public boolean existsByProductIdAndClientId(Long productId, Long clientId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM subscribers WHERE product_id = ? AND client_id = ?",
                Integer.class, productId, clientId);
        return count != null && count > 0;
    }

    public boolean deleteByProductIdAndClientId(Long productId, Long clientId) {
        int rows = jdbcTemplate.update(
                "DELETE FROM subscribers WHERE product_id = ? AND client_id = ?",
                productId, clientId);
        return rows > 0;
    }

    public Slice<Long> findProductIdsByClientId(Long clientId, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        String sql = "SELECT product_id FROM subscribers WHERE client_id = ? LIMIT ? OFFSET ?";
        List<Long> productIds = jdbcTemplate.queryForList(sql, Long.class, clientId, pageSize + 1, pageable.getOffset());

        List<Long> resultList = new ArrayList<>(productIds);
        boolean hasNext = resultList.size() > pageSize;
        if (hasNext) {
            resultList.remove(pageSize);
        }
        return new SliceImpl<>(resultList, pageable, hasNext);
    }

    public void save(Subscriber subscriber) {
        jdbcTemplate.update("INSERT INTO subscribers (product_id, client_id) VALUES (?, ?) ON CONFLICT (product_id, client_id) DO NOTHING",
                subscriber.productId(), subscriber.clientId());
    }
}
