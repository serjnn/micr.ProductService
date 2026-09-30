package com.serjnn.ProductService.externaltask.worker;

import com.serjnn.ProductService.externaltask.client.ExternalServiceClient;
import com.serjnn.ProductService.externaltask.config.ExternalTaskProperties;
import com.serjnn.ProductService.externaltask.dto.ExternalServiceResponse;
import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.models.ExternalTask;
import com.serjnn.ProductService.externaltask.repo.ExternalTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetReconciliationWorkerTest {

    @Mock
    private ExternalTaskRepository taskRepository;

    @Mock
    private ExternalServiceClient externalClient;

    private ExternalTaskProperties properties;
    private GetReconciliationWorker worker;

    @BeforeEach
    void setUp() {
        properties = new ExternalTaskProperties(2000, 3000, 10, 5, 5, 2, "http://supplier/api/v1/products", 3000, 3000);
        worker = new GetReconciliationWorker(taskRepository, externalClient, properties);
    }

    @Test
    @DisplayName("Should do nothing when no FAILED_CHECK_NEEDED tasks exist")
    void shouldDoNothingWhenNoFailedCheckNeededTasks() {
        when(taskRepository.claimBatchForProcessing(eq(ExternalTaskState.FAILED_CHECK_NEEDED), eq(ExternalTaskState.IN_FLIGHT_AUDIT), eq(10)))
                .thenReturn(Collections.emptyList());

        worker.reconcileUncertainTasks();

        verify(externalClient, never()).checkTaskStatus(any());
        verify(taskRepository, never()).markAsSucceeded(anyLong(), any());
    }

    @Test
    @DisplayName("Should mark SUCCEEDED when audit GET returns existing record on remote server")
    void shouldMarkSucceededWhenRemoteRecordFound() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(1L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.IN_FLIGHT_AUDIT,
                "{\"productId\":100}", null, 0, 5, Instant.now(), "Timeout", Instant.now(), Instant.now());

        when(taskRepository.claimBatchForProcessing(eq(ExternalTaskState.FAILED_CHECK_NEEDED), eq(ExternalTaskState.IN_FLIGHT_AUDIT), eq(10)))
                .thenReturn(List.of(task));
        when(externalClient.checkTaskStatus(businessKey))
                .thenReturn(Optional.of(new ExternalServiceResponse("EXT-REMOTE-123", "SUCCESS", "Found")));

        worker.reconcileUncertainTasks();

        verify(taskRepository).markAsSucceeded(1L, "EXT-REMOTE-123");
    }

    @Test
    @DisplayName("Should reset to PENDING with exponential backoff when audit GET returns 404 (safe to retry)")
    void shouldResetToPendingWhenRemoteRecordNotFound() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(2L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.IN_FLIGHT_AUDIT,
                "{\"productId\":101}", null, 1, 5, Instant.now(), "Timeout", Instant.now(), Instant.now());

        when(taskRepository.claimBatchForProcessing(eq(ExternalTaskState.FAILED_CHECK_NEEDED), eq(ExternalTaskState.IN_FLIGHT_AUDIT), eq(10)))
                .thenReturn(List.of(task));
        when(externalClient.checkTaskStatus(businessKey))
                .thenReturn(Optional.empty()); // 404 Not Found

        worker.reconcileUncertainTasks();

        verify(taskRepository).markAsPendingForRetry(eq(2L), eq(2), any(Instant.class));
    }

    @Test
    @DisplayName("Should mark FATAL_FAILED when retries are exhausted and record still not found")
    void shouldMarkFatalFailedWhenRetriesExhausted() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(3L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.IN_FLIGHT_AUDIT,
                "{\"productId\":102}", null, 5, 5, Instant.now(), "Timeout", Instant.now(), Instant.now());

        when(taskRepository.claimBatchForProcessing(eq(ExternalTaskState.FAILED_CHECK_NEEDED), eq(ExternalTaskState.IN_FLIGHT_AUDIT), eq(10)))
                .thenReturn(List.of(task));
        when(externalClient.checkTaskStatus(businessKey))
                .thenReturn(Optional.empty());

        worker.reconcileUncertainTasks();

        verify(taskRepository).markAsFatalFailed(eq(3L), contains("Exceeded max retries"));
    }

    @Test
    @DisplayName("Should keep in FAILED_CHECK_NEEDED and reschedule when audit GET encounters transient error")
    void shouldKeepFailedCheckNeededOnAuditNetworkError() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(4L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.IN_FLIGHT_AUDIT,
                "{\"productId\":103}", null, 0, 5, Instant.now(), "Timeout", Instant.now(), Instant.now());

        when(taskRepository.claimBatchForProcessing(eq(ExternalTaskState.FAILED_CHECK_NEEDED), eq(ExternalTaskState.IN_FLIGHT_AUDIT), eq(10)))
                .thenReturn(List.of(task));
        when(externalClient.checkTaskStatus(businessKey))
                .thenThrow(new ResourceAccessException("Audit timeout"));

        worker.reconcileUncertainTasks();

        verify(taskRepository).markAsFailedCheckNeeded(eq(4L), contains("Audit failed: Audit timeout"), any(Instant.class));
    }

    @Test
    @DisplayName("Should mark FATAL_FAILED on permanent 401 Unauthorized during audit")
    void shouldMarkFatalFailedOn401UnauthorizedAudit() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(5L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.IN_FLIGHT_AUDIT,
                "{\"productId\":105}", null, 0, 5, Instant.now(), "Timeout", Instant.now(), Instant.now());

        when(taskRepository.claimBatchForProcessing(eq(ExternalTaskState.FAILED_CHECK_NEEDED), eq(ExternalTaskState.IN_FLIGHT_AUDIT), eq(10)))
                .thenReturn(List.of(task));
        when(externalClient.checkTaskStatus(businessKey))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        worker.reconcileUncertainTasks();

        verify(taskRepository).markAsFatalFailed(eq(5L), contains("Non-recoverable audit client error"));
    }
}
