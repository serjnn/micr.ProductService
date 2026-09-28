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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostDispatchWorkerTest {

    @Mock
    private ExternalTaskRepository taskRepository;

    @Mock
    private ExternalServiceClient externalClient;

    private ExternalTaskProperties properties;
    private PostDispatchWorker worker;

    @BeforeEach
    void setUp() {
        properties = new ExternalTaskProperties(2000, 3000, 10, 5, 5, 2, "http://supplier/api/v1/products", 3000, 3000);
        worker = new PostDispatchWorker(taskRepository, externalClient, properties);
    }

    @Test
    @DisplayName("Should do nothing when no pending tasks exist")
    void shouldDoNothingWhenNoPendingTasks() {
        when(taskRepository.lockBatchForProcessing(eq(ExternalTaskState.PENDING), eq(10)))
                .thenReturn(Collections.emptyList());

        worker.dispatchPendingTasks();

        verify(externalClient, never()).postTask(any(), any(), any());
        verify(taskRepository, never()).markAsSucceeded(anyLong(), any());
    }

    @Test
    @DisplayName("Should dispatch task and mark SUCCEEDED on successful POST response")
    void shouldDispatchAndMarkSucceeded() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(1L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.PENDING,
                "{\"productId\":100}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(taskRepository.lockBatchForProcessing(eq(ExternalTaskState.PENDING), eq(10)))
                .thenReturn(List.of(task));
        when(externalClient.postTask(businessKey, "SUPPLIER_SYNC", "{\"productId\":100}"))
                .thenReturn(new ExternalServiceResponse("EXT-999", "CONFIRMED", "OK"));

        worker.dispatchPendingTasks();

        verify(taskRepository).markAsSucceeded(1L, "EXT-999");
    }

    @Test
    @DisplayName("Should mark FAILED_CHECK_NEEDED on ResourceAccessException (network timeout)")
    void shouldMarkFailedCheckNeededOnTimeout() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(2L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.PENDING,
                "{\"productId\":101}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(taskRepository.lockBatchForProcessing(eq(ExternalTaskState.PENDING), eq(10)))
                .thenReturn(List.of(task));
        when(externalClient.postTask(businessKey, "SUPPLIER_SYNC", "{\"productId\":101}"))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        worker.dispatchPendingTasks();

        verify(taskRepository).markAsFailedCheckNeeded(eq(2L), contains("Connection timed out"), any(Instant.class));
    }

    @Test
    @DisplayName("Should mark FAILED_CHECK_NEEDED on 504 Gateway Timeout / 5xx Server Error")
    void shouldMarkFailedCheckNeededOn5xx() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(3L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.PENDING,
                "{\"productId\":102}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(taskRepository.lockBatchForProcessing(eq(ExternalTaskState.PENDING), eq(10)))
                .thenReturn(List.of(task));
        when(externalClient.postTask(businessKey, "SUPPLIER_SYNC", "{\"productId\":102}"))
                .thenThrow(new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "Gateway Timeout"));

        worker.dispatchPendingTasks();

        verify(taskRepository).markAsFailedCheckNeeded(eq(3L), contains("504"), any(Instant.class));
    }

    @Test
    @DisplayName("Should mark FATAL_FAILED on 400 Bad Request")
    void shouldMarkFatalFailedOn400BadRequest() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(4L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.PENDING,
                "{\"invalid\":true}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(taskRepository.lockBatchForProcessing(eq(ExternalTaskState.PENDING), eq(10)))
                .thenReturn(List.of(task));
        when(externalClient.postTask(businessKey, "SUPPLIER_SYNC", "{\"invalid\":true}"))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid format"));

        worker.dispatchPendingTasks();

        verify(taskRepository).markAsFatalFailed(eq(4L), contains("400"));
    }
}
