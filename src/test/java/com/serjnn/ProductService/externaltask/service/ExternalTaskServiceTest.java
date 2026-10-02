package com.serjnn.ProductService.externaltask.service;

import com.serjnn.ProductService.externaltask.config.ExternalTaskProperties;
import com.serjnn.ProductService.externaltask.dto.CreateExternalTaskRequest;
import com.serjnn.ProductService.externaltask.dto.ExternalTaskResponse;
import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.exception.ExternalTaskNotFoundException;
import com.serjnn.ProductService.externaltask.models.ExternalTask;
import com.serjnn.ProductService.externaltask.repo.ExternalTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalTaskServiceTest {

    @Mock
    private ExternalTaskRepository taskRepository;

    private ExternalTaskProperties properties;
    private ExternalTaskService taskService;

    @BeforeEach
    void setUp() {
        properties = new ExternalTaskProperties(2000, 3000, 10, 5, 5, 2, "http://supplier/api/v1/products", 3000, 3000);
        taskService = new ExternalTaskService(taskRepository, properties);
    }

    @Test
    @DisplayName("Should submit task with generated business key and default max retries")
    void shouldSubmitTaskWithDefaults() {
        CreateExternalTaskRequest request = new CreateExternalTaskRequest("SUPPLIER_SYNC", "{\"sku\":\"ABC\"}", null);
        ExternalTask savedTask = new ExternalTask(10L, UUID.randomUUID(), "SUPPLIER_SYNC", ExternalTaskState.PENDING,
                "{\"sku\":\"ABC\"}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(taskRepository.save(any(ExternalTask.class))).thenReturn(10L);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(savedTask));

        ExternalTaskResponse response = taskService.submitTask(request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("SUPPLIER_SYNC", response.taskType());
        assertEquals(ExternalTaskState.PENDING, response.state());
        assertEquals(5, response.maxRetries());
    }

    @Test
    @DisplayName("Should generate UUIDv7 for businessKey when submitting task")
    void shouldGenerateUUIDv7ForBusinessKey() {
        CreateExternalTaskRequest request = new CreateExternalTaskRequest("SUPPLIER_SYNC", "{\"sku\":\"ABC\"}", null);
        org.mockito.ArgumentCaptor<ExternalTask> captor = org.mockito.ArgumentCaptor.forClass(ExternalTask.class);

        when(taskRepository.save(captor.capture())).thenReturn(1L);
        ExternalTask savedTask = new ExternalTask(1L, UUID.randomUUID(), "SUPPLIER_SYNC", ExternalTaskState.PENDING,
                "{\"sku\":\"ABC\"}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());
        when(taskRepository.findById(1L)).thenReturn(Optional.of(savedTask));

        taskService.submitTask(request);

        ExternalTask captured = captor.getValue();
        assertNotNull(captured.businessKey());
        assertEquals(7, captured.businessKey().version(), "Generated business key should be UUID version 7");
    }

    @Test
    @DisplayName("Should get task by business key")
    void shouldGetTaskByBusinessKey() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(1L, businessKey, "SUPPLIER_SYNC", ExternalTaskState.SUCCEEDED,
                "{}", "EXT-1", 0, 5, Instant.now(), null, Instant.now(), Instant.now());

        when(taskRepository.findByBusinessKey(businessKey)).thenReturn(Optional.of(task));

        ExternalTaskResponse response = taskService.getTaskByBusinessKey(businessKey);

        assertNotNull(response);
        assertEquals(businessKey, response.businessKey());
        assertEquals(ExternalTaskState.SUCCEEDED, response.state());
    }

    @Test
    @DisplayName("Should throw ExternalTaskNotFoundException when business key does not exist")
    void shouldThrowWhenBusinessKeyNotFound() {
        UUID businessKey = UUID.randomUUID();
        when(taskRepository.findByBusinessKey(businessKey)).thenReturn(Optional.empty());

        assertThrows(ExternalTaskNotFoundException.class, () -> taskService.getTaskByBusinessKey(businessKey));
    }

    @Test
    @DisplayName("Should get tasks by state")
    void shouldGetTasksByState() {
        ExternalTask task = new ExternalTask(1L, UUID.randomUUID(), "SUPPLIER_SYNC", ExternalTaskState.PENDING,
                "{}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now());
        when(taskRepository.findAllByState(eq(ExternalTaskState.PENDING), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(task)));

        Slice<ExternalTaskResponse> result = taskService.getTasks(ExternalTaskState.PENDING, PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
        assertEquals(ExternalTaskState.PENDING, result.getContent().get(0).state());
    }
}
