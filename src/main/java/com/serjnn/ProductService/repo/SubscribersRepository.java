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

    public void save(Subscriber subscriber) {
        jdbcTemplate.update("INSERT INTO subscribers (product_id, client_id) VALUES (?, ?)",
                subscriber.productId(), subscriber.clientId());
    }
}
