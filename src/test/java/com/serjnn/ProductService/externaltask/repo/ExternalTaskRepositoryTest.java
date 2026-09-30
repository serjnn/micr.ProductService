package com.serjnn.ProductService.externaltask.repo;

import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.models.ExternalTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalTaskRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ExternalTaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository = new ExternalTaskRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("Should save task and return generated ID using RETURNING id query")
    void shouldSaveTask() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = ExternalTask.newPendingTask(businessKey, "SYNC", "{}", 5);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(42L);

        Long savedId = taskRepository.save(task);

        assertEquals(42L, savedId);
        verify(jdbcTemplate).queryForObject(contains("RETURNING id"), eq(Long.class), eq(businessKey), eq("SYNC"), eq("PENDING"), eq("{}"), isNull(), eq(0), eq(5), any(Timestamp.class), isNull(), any(Timestamp.class), any(Timestamp.class));
    }

    @Test
    @DisplayName("Should atomically claim batch for processing using CTE")
    void shouldClaimBatchForProcessing() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(1L, businessKey, "SYNC", ExternalTaskState.IN_FLIGHT_POST,
                "{}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("PENDING"), eq(10), eq("IN_FLIGHT_POST")))
                .thenReturn(List.of(task));

        List<ExternalTask> result = taskRepository.claimBatchForProcessing(ExternalTaskState.PENDING, ExternalTaskState.IN_FLIGHT_POST, 10);

        assertEquals(1, result.size());
        assertEquals(ExternalTaskState.IN_FLIGHT_POST, result.get(0).state());
        verify(jdbcTemplate).query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class), eq("PENDING"), eq(10), eq("IN_FLIGHT_POST"));
    }

    @Test
    @DisplayName("Should recover stale in-flight tasks")
    void shouldRecoverStaleInFlightTasks() {
        when(jdbcTemplate.update(anyString(), any(Timestamp.class)))
                .thenReturn(3);

        int recovered = taskRepository.recoverStaleInFlightTasks(java.time.Duration.ofMinutes(5));

        assertEquals(3, recovered);
        verify(jdbcTemplate).update(contains("WHERE state IN ('IN_FLIGHT_POST', 'IN_FLIGHT_AUDIT')"), any(Timestamp.class));
    }

    @Test
    @DisplayName("Should lock batch for processing using SKIP LOCKED query")
    void shouldLockBatchForProcessing() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(1L, businessKey, "SYNC", ExternalTaskState.PENDING,
                "{}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("PENDING"), eq(10)))
                .thenReturn(List.of(task));

        List<ExternalTask> result = taskRepository.lockBatchForProcessing(ExternalTaskState.PENDING, 10);

        assertEquals(1, result.size());
        assertEquals(businessKey, result.get(0).businessKey());
        verify(jdbcTemplate).query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class), eq("PENDING"), eq(10));
    }

    @Test
    @DisplayName("Should find task by business key")
    void shouldFindByBusinessKey() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(1L, businessKey, "SYNC", ExternalTaskState.SUCCEEDED,
                "{}", "EXT-1", 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(businessKey)))
                .thenReturn(List.of(task));

        Optional<ExternalTask> result = taskRepository.findByBusinessKey(businessKey);

        assertTrue(result.isPresent());
        assertEquals("EXT-1", result.get().externalResourceId());
    }

    @Test
    @DisplayName("Should find task by ID")
    void shouldFindById() {
        ExternalTask task = new ExternalTask(1L, UUID.randomUUID(), "SYNC", ExternalTaskState.SUCCEEDED,
                "{}", "EXT-1", 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(task));

        Optional<ExternalTask> result = taskRepository.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().id());
    }

    @Test
    @DisplayName("Should return empty optional when not found")
    void shouldReturnEmptyWhenNotFound() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong()))
                .thenReturn(Collections.emptyList());

        Optional<ExternalTask> result = taskRepository.findById(999L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should mark task as succeeded")
    void shouldMarkAsSucceeded() {
        when(jdbcTemplate.update(anyString(), eq("EXT-RES-1"), eq(1L)))
                .thenReturn(1);

        boolean updated = taskRepository.markAsSucceeded(1L, "EXT-RES-1");

        assertTrue(updated);
        verify(jdbcTemplate).update(contains("SET state = 'SUCCEEDED'"), eq("EXT-RES-1"), eq(1L));
    }

    @Test
    @DisplayName("Should mark task as FAILED_CHECK_NEEDED")
    void shouldMarkAsFailedCheckNeeded() {
        Instant nextRetry = Instant.now().plusSeconds(10);
        when(jdbcTemplate.update(anyString(), eq("Network timeout"), any(Timestamp.class), eq(2L)))
                .thenReturn(1);

        boolean updated = taskRepository.markAsFailedCheckNeeded(2L, "Network timeout", nextRetry);

        assertTrue(updated);
        verify(jdbcTemplate).update(contains("SET state = 'FAILED_CHECK_NEEDED'"), eq("Network timeout"), any(Timestamp.class), eq(2L));
    }

    @Test
    @DisplayName("Should mark task as pending for retry with incremented count")
    void shouldMarkAsPendingForRetry() {
        Instant nextRetry = Instant.now().plusSeconds(4);
        when(jdbcTemplate.update(anyString(), eq(2), any(Timestamp.class), eq(3L)))
                .thenReturn(1);

        boolean updated = taskRepository.markAsPendingForRetry(3L, 2, nextRetry);

        assertTrue(updated);
        verify(jdbcTemplate).update(contains("SET state = 'PENDING'"), eq(2), any(Timestamp.class), eq(3L));
    }

    @Test
    @DisplayName("Should mark task as fatal failed")
    void shouldMarkAsFatalFailed() {
        when(jdbcTemplate.update(anyString(), eq("400 Bad Request"), eq(4L)))
                .thenReturn(1);

        boolean updated = taskRepository.markAsFatalFailed(4L, "400 Bad Request");

        assertTrue(updated);
        verify(jdbcTemplate).update(contains("SET state = 'FATAL_FAILED'"), eq("400 Bad Request"), eq(4L));
    }

    @Test
    @DisplayName("Should retrieve paginated slice of tasks")
    void shouldRetrievePaginatedSlice() {
        UUID k1 = UUID.randomUUID();
        UUID k2 = UUID.randomUUID();
        ExternalTask t1 = new ExternalTask(1L, k1, "SYNC", ExternalTaskState.PENDING, "{}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());
        ExternalTask t2 = new ExternalTask(2L, k2, "SYNC", ExternalTaskState.PENDING, "{}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(3), eq(0L)))
                .thenReturn(new ArrayList<>(List.of(t1, t2)));

        Slice<ExternalTask> slice = taskRepository.findAll(PageRequest.of(0, 2));

        assertEquals(2, slice.getContent().size());
        assertFalse(slice.hasNext());
    }
}
