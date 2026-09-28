package com.serjnn.ProductService.externaltask.repo;

import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.models.ExternalTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ExternalTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ExternalTask> rowMapper = (rs, rowNum) -> {
        Timestamp nextRetryAtTs = rs.getTimestamp("next_retry_at");
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");

        Object businessKeyObj = rs.getObject("business_key");
        UUID businessKey;
        if (businessKeyObj instanceof UUID uuid) {
            businessKey = uuid;
        } else if (businessKeyObj != null) {
            businessKey = UUID.fromString(businessKeyObj.toString());
        } else {
            businessKey = null;
        }

        return new ExternalTask(
                rs.getLong("id"),
                businessKey,
                rs.getString("task_type"),
                ExternalTaskState.valueOf(rs.getString("state")),
                rs.getString("payload"),
                rs.getString("external_resource_id"),
                rs.getInt("retry_count"),
                rs.getInt("max_retries"),
                nextRetryAtTs != null ? nextRetryAtTs.toInstant() : null,
                rs.getString("last_error"),
                createdAtTs != null ? createdAtTs.toInstant() : null,
                updatedAtTs != null ? updatedAtTs.toInstant() : null
        );
    };

    public Long save(ExternalTask task) {
        String sql = """
                INSERT INTO external_task_state (
                    business_key, task_type, state, payload, external_resource_id,
                    retry_count, max_retries, next_retry_at, last_error, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, task.businessKey());
            ps.setString(2, task.taskType());
            ps.setString(3, task.state().name());
            ps.setString(4, task.payload());
            ps.setString(5, task.externalResourceId());
            ps.setInt(6, task.retryCount());
            ps.setInt(7, task.maxRetries());
            ps.setTimestamp(8, task.nextRetryAt() != null ? Timestamp.from(task.nextRetryAt()) : Timestamp.from(Instant.now()));
            ps.setString(9, task.lastError());
            ps.setTimestamp(10, task.createdAt() != null ? Timestamp.from(task.createdAt()) : Timestamp.from(Instant.now()));
            ps.setTimestamp(11, task.updatedAt() != null ? Timestamp.from(task.updatedAt()) : Timestamp.from(Instant.now()));
            return ps;
        }, keyHolder);

        Number key = (Number) keyHolder.getKeys().get("id");
        return key != null ? key.longValue() : null;
    }

    public Optional<ExternalTask> findById(Long id) {
        String sql = "SELECT * FROM external_task_state WHERE id = ?";
        List<ExternalTask> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }

    public Optional<ExternalTask> findByBusinessKey(UUID businessKey) {
        String sql = "SELECT * FROM external_task_state WHERE business_key = ?";
        List<ExternalTask> results = jdbcTemplate.query(sql, rowMapper, businessKey);
        return results.stream().findFirst();
    }

    public List<ExternalTask> lockBatchForProcessing(ExternalTaskState state, int limit) {
        String sql = """
                SELECT id, business_key, task_type, state, payload, external_resource_id,
                       retry_count, max_retries, next_retry_at, last_error, created_at, updated_at
                FROM external_task_state
                WHERE state = ?
                  AND next_retry_at <= NOW()
                ORDER BY next_retry_at ASC, id ASC
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """;
        return jdbcTemplate.query(sql, rowMapper, state.name(), limit);
    }

    public Slice<ExternalTask> findAll(Pageable pageable) {
        int pageSize = pageable.getPageSize();
        String sql = "SELECT * FROM external_task_state ORDER BY id DESC LIMIT ? OFFSET ?";
        List<ExternalTask> tasks = jdbcTemplate.query(sql, rowMapper, pageSize + 1, pageable.getOffset());

        boolean hasNext = tasks.size() > pageSize;
        if (hasNext) {
            tasks.remove(pageSize);
        }
        return new SliceImpl<>(tasks, pageable, hasNext);
    }

    public Slice<ExternalTask> findAllByState(ExternalTaskState state, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        String sql = "SELECT * FROM external_task_state WHERE state = ? ORDER BY id DESC LIMIT ? OFFSET ?";
        List<ExternalTask> tasks = jdbcTemplate.query(sql, rowMapper, state.name(), pageSize + 1, pageable.getOffset());

        boolean hasNext = tasks.size() > pageSize;
        if (hasNext) {
            tasks.remove(pageSize);
        }
        return new SliceImpl<>(tasks, pageable, hasNext);
    }

    public boolean markAsSucceeded(Long id, String externalResourceId) {
        String sql = """
                UPDATE external_task_state
                SET state = 'SUCCEEDED',
                    external_resource_id = ?,
                    last_error = NULL,
                    updated_at = NOW()
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, externalResourceId, id) > 0;
    }

    public boolean markAsFailedCheckNeeded(Long id, String lastError, Instant nextRetryAt) {
        String sql = """
                UPDATE external_task_state
                SET state = 'FAILED_CHECK_NEEDED',
                    last_error = ?,
                    next_retry_at = ?,
                    updated_at = NOW()
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, lastError, Timestamp.from(nextRetryAt), id) > 0;
    }

    public boolean markAsPendingForRetry(Long id, int nextRetryCount, Instant nextRetryAt) {
        String sql = """
                UPDATE external_task_state
                SET state = 'PENDING',
                    retry_count = ?,
                    next_retry_at = ?,
                    updated_at = NOW()
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, nextRetryCount, Timestamp.from(nextRetryAt), id) > 0;
    }

    public boolean markAsFatalFailed(Long id, String lastError) {
        String sql = """
                UPDATE external_task_state
                SET state = 'FATAL_FAILED',
                    last_error = ?,
                    updated_at = NOW()
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, lastError, id) > 0;
    }
}
