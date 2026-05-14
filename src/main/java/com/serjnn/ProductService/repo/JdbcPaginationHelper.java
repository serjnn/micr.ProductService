package com.serjnn.ProductService.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JdbcPaginationHelper {

    public <T> Slice<T> queryForSlice(JdbcTemplate jdbcTemplate, String baseSql, RowMapper<T> rowMapper, Pageable pageable, Object... args) {
        int pageSize = pageable.getPageSize();
        Object[] params = new Object[args.length + 2];
        System.arraycopy(args, 0, params, 0, args.length);
        params[args.length] = pageSize + 1;
        params[args.length + 1] = pageable.getOffset();

        String sql = baseSql + " LIMIT ? OFFSET ?";
        List<T> results = jdbcTemplate.query(sql, rowMapper, params);

        return createSlice(results, pageable);
    }

    public <T> Slice<T> queryForSlice(JdbcTemplate jdbcTemplate, String baseSql, Class<T> elementType, Pageable pageable, Object... args) {
        int pageSize = pageable.getPageSize();
        Object[] params = new Object[args.length + 2];
        System.arraycopy(args, 0, params, 0, args.length);
        params[args.length] = pageSize + 1;
        params[args.length + 1] = pageable.getOffset();

        String sql = baseSql + " LIMIT ? OFFSET ?";
        List<T> results = jdbcTemplate.queryForList(sql, elementType, params);

        return createSlice(results, pageable);
    }

    public <T> Slice<T> queryForSlice(NamedParameterJdbcTemplate namedTemplate, String baseSql, MapSqlParameterSource parameters, RowMapper<T> rowMapper, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        parameters.addValue("limit", pageSize + 1);
        parameters.addValue("offset", pageable.getOffset());

        String sql = baseSql + " LIMIT :limit OFFSET :offset";
        List<T> results = namedTemplate.query(sql, parameters, rowMapper);

        return createSlice(results, pageable);
    }

    private <T> Slice<T> createSlice(List<T> results, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        boolean hasNext = results.size() > pageSize;
        List<T> content = hasNext ? new ArrayList<>(results.subList(0, pageSize)) : results;
        return new SliceImpl<>(content, pageable, hasNext);
    }
}
