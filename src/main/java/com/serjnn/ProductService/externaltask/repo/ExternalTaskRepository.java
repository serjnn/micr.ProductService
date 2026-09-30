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
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ExternalTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_COLUMNS = """
            id, business_key, task_type, state, payload, external_resource_id,
            retry_count, max_retries, next_retry_at, last_error, created_at, updated_at
            """;

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
                RETURNING id
                """;

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                task.businessKey(),
                task.taskType(),
                task.state().name(),
                task.payload(),
                task.externalResourceId(),
                task.retryCount(),
                task.maxRetries(),
                task.nextRetryAt() != null ? Timestamp.from(task.nextRetryAt()) : Timestamp.from(Instant.now()),
                task.lastError(),
                task.createdAt() != null ? Timestamp.from(task.createdAt()) : Timestamp.from(Instant.now()),
                task.updatedAt() != null ? Timestamp.from(task.updatedAt()) : Timestamp.from(Instant.now())
        );
    }

    public Optional<ExternalTask> findById(Long id) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM external_task_state WHERE id = ?";
        List<ExternalTask> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }

    public Optional<ExternalTask> findByBusinessKey(UUID businessKey) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM external_task_state WHERE business_key = ?";
        List<ExternalTask> results = jdbcTemplate.query(sql, rowMapper, businessKey);
        return results.stream().findFirst();
    }

    public List<ExternalTask> claimBatchForProcessing(ExternalTaskState fromState, ExternalTaskState toState, int limit) {
        String sql = """
                WITH candidate AS (
                    SELECT id
                    FROM external_task_state
                    WHERE state = ?
                      AND next_retry_at <= NOW()
                    ORDER BY next_retry_at ASC, id ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE external_task_state target
                SET state = ?,
                    updated_at = NOW()
                FROM candidate
                WHERE target.id = candidate.id
                RETURNING target.id, target.business_key, target.task_type, target.state, target.payload,
                          target.external_resource_id, target.retry_count, target.max_retries,
                          target.next_retry_at, target.last_error, target.created_at, target.updated_at
                """;
        return jdbcTemplate.query(sql, rowMapper, fromState.name(), limit, toState.name());
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

    public int recoverStaleInFlightTasks(Duration staleThreshold) {
        String sql = """
                UPDATE external_task_state
                SET state = CASE
                        WHEN state = 'IN_FLIGHT_POST' THEN 'PENDING'
                        WHEN state = 'IN_FLIGHT_AUDIT' THEN 'FAILED_CHECK_NEEDED'
                        ELSE state
                    END,
                    last_error = 'Recovered from stale in-flight execution',
                    updated_at = NOW()
                WHERE state IN ('IN_FLIGHT_POST', 'IN_FLIGHT_AUDIT')
                  AND updated_at <= ?
                """;
        Timestamp thresholdTs = Timestamp.from(Instant.now().minus(staleThreshold));
        return jdbcTemplate.update(sql, thresholdTs);
    }

    public Slice<ExternalTask> findAll(Pageable pageable) {
        int pageSize = pageable.getPageSize();
        String sql = "SELECT " + SELECT_COLUMNS + " FROM external_task_state ORDER BY id DESC LIMIT ? OFFSET ?";
        List<ExternalTask> tasks = jdbcTemplate.query(sql, rowMapper, pageSize + 1, pageable.getOffset());

        boolean hasNext = tasks.size() > pageSize;
        if (hasNext) {
            tasks.remove(pageSize);
        }
        return new SliceImpl<>(tasks, pageable, hasNext);
    }

    public Slice<ExternalTask> findAllByState(ExternalTaskState state, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        String sql = "SELECT " + SELECT_COLUMNS + " FROM external_task_state WHERE state = ? ORDER BY id DESC LIMIT ? OFFSET ?";
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
