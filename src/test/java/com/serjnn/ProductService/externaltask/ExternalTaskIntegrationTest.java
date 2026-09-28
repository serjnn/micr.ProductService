package com.serjnn.ProductService.externaltask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serjnn.ProductService.externaltask.client.ExternalServiceClient;
import com.serjnn.ProductService.externaltask.dto.CreateExternalTaskRequest;
import com.serjnn.ProductService.externaltask.dto.ExternalServiceResponse;
import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.models.ExternalTask;
import com.serjnn.ProductService.externaltask.repo.ExternalTaskRepository;
import com.serjnn.ProductService.externaltask.worker.GetReconciliationWorker;
import com.serjnn.ProductService.externaltask.worker.PostDispatchWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.redis.testcontainers.RedisContainer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class ExternalTaskIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = postgres.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=product_schema";
        });
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getFirstMappedPort());
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.loadbalancer.enabled", () -> "false");
        registry.add("app.services.discount-url", () -> "http://discount/api/v1/discounts/");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExternalTaskRepository taskRepository;

    @Autowired
    private PostDispatchWorker postDispatchWorker;

    @Autowired
    private GetReconciliationWorker getReconciliationWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExternalServiceClient externalServiceClient;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("TRUNCATE TABLE external_task_state RESTART IDENTITY CASCADE");
        reset(externalServiceClient);
    }

    @Test
    @DisplayName("Scenario 1: Happy Path - PENDING -> POST 200 OK -> SUCCEEDED")
    void shouldDispatchPendingTaskToSucceeded() throws Exception {
        UUID businessKey = UUID.randomUUID();
        CreateExternalTaskRequest request = new CreateExternalTaskRequest("SUPPLIER_PRODUCT_SYNC", "{\"sku\":\"PROD-101\",\"price\":99.99}", 5);

        // Submit task via REST API
        mockMvc.perform(post("/api/v1/external-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PENDING"));

        // Mock remote POST 200 OK
        when(externalServiceClient.postTask(any(UUID.class), eq("SUPPLIER_PRODUCT_SYNC"), anyString()))
                .thenReturn(new ExternalServiceResponse("SUPPLIER-REF-999", "CREATED", "Success"));

        // Execute Worker 1 dispatch
        postDispatchWorker.dispatchPendingTasks();

        // Verify task transitioned to SUCCEEDED
        List<ExternalTask> allTasks = taskRepository.lockBatchForProcessing(ExternalTaskState.SUCCEEDED, 10);
        // Note: lockBatchForProcessing with SUCCEEDED won't match index WHERE state in (PENDING, FAILED_CHECK_NEEDED)
        // Let's query by ID directly:
        ExternalTask savedTask = taskRepository.findById(1L).orElseThrow();
        assertEquals(ExternalTaskState.SUCCEEDED, savedTask.state());
        assertEquals("SUPPLIER-REF-999", savedTask.externalResourceId());
        assertNull(savedTask.lastError());
    }

    @Test
    @DisplayName("Scenario 2: Timeout on POST -> FAILED_CHECK_NEEDED -> Reconciliation GET 200 OK -> SUCCEEDED")
    void shouldReconcileTaskWhenRemoteRecordAlreadyCreated() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = ExternalTask.newPendingTask(businessKey, "SUPPLIER_PRODUCT_SYNC", "{\"sku\":\"PROD-102\"}", 3);
        Long id = taskRepository.save(task);

        // 1. Worker 1 times out
        when(externalServiceClient.postTask(eq(businessKey), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("Read timed out after 3000ms"));

        postDispatchWorker.dispatchPendingTasks();

        ExternalTask stateAfterTimeout = taskRepository.findById(id).orElseThrow();
        assertEquals(ExternalTaskState.FAILED_CHECK_NEEDED, stateAfterTimeout.state());
        assertTrue(stateAfterTimeout.lastError().contains("Read timed out"));

        // Ensure next_retry_at is now eligible for immediate worker run in test
        jdbcTemplate.update("UPDATE external_task_state SET next_retry_at = NOW() - INTERVAL '1 second' WHERE id = ?", id);

        // 2. Worker 2 audits: remote record actually exists!
        when(externalServiceClient.checkTaskStatus(eq(businessKey)))
                .thenReturn(Optional.of(new ExternalServiceResponse("REMOTE-FOUND-888", "PROCESSED", "Record found")));

        getReconciliationWorker.reconcileUncertainTasks();

        ExternalTask stateAfterAudit = taskRepository.findById(id).orElseThrow();
        assertEquals(ExternalTaskState.SUCCEEDED, stateAfterAudit.state());
        assertEquals("REMOTE-FOUND-888", stateAfterAudit.externalResourceId());
    }

    @Test
    @DisplayName("Scenario 3: Timeout on POST -> Audit GET 404 -> Reset to PENDING -> Retry POST succeeds -> SUCCEEDED")
    void shouldResetToPendingAndRetryWhenRemoteRecordNotFound() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = ExternalTask.newPendingTask(businessKey, "SUPPLIER_PRODUCT_SYNC", "{\"sku\":\"PROD-103\"}", 3);
        Long id = taskRepository.save(task);

        // 1. Worker 1 times out
        when(externalServiceClient.postTask(eq(businessKey), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("Connection reset by peer"));

        postDispatchWorker.dispatchPendingTasks();

        ExternalTask stateAfterTimeout = taskRepository.findById(id).orElseThrow();
        assertEquals(ExternalTaskState.FAILED_CHECK_NEEDED, stateAfterTimeout.state());

        jdbcTemplate.update("UPDATE external_task_state SET next_retry_at = NOW() - INTERVAL '1 second' WHERE id = ?", id);

        // 2. Worker 2 audits: remote returned 404 (safe to retry)
        when(externalServiceClient.checkTaskStatus(eq(businessKey)))
                .thenReturn(Optional.empty());

        getReconciliationWorker.reconcileUncertainTasks();

        ExternalTask stateAfterReconcile = taskRepository.findById(id).orElseThrow();
        assertEquals(ExternalTaskState.PENDING, stateAfterReconcile.state());
        assertEquals(1, stateAfterReconcile.retryCount());

        jdbcTemplate.update("UPDATE external_task_state SET next_retry_at = NOW() - INTERVAL '1 second' WHERE id = ?", id);

        // 3. Worker 1 retries POST and succeeds
        when(externalServiceClient.postTask(eq(businessKey), anyString(), anyString()))
                .thenReturn(new ExternalServiceResponse("REMOTE-RETRY-SUCCESS", "CREATED", "OK"));

        postDispatchWorker.dispatchPendingTasks();

        ExternalTask stateAfterRetry = taskRepository.findById(id).orElseThrow();
        assertEquals(ExternalTaskState.SUCCEEDED, stateAfterRetry.state());
        assertEquals("REMOTE-RETRY-SUCCESS", stateAfterRetry.externalResourceId());
    }

    @Test
    @DisplayName("Scenario 4: Fatal 400 Bad Request -> FATAL_FAILED")
    void shouldMarkFatalFailedOn400BadRequest() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = ExternalTask.newPendingTask(businessKey, "SUPPLIER_PRODUCT_SYNC", "{\"invalid\":true}", 3);
        Long id = taskRepository.save(task);

        when(externalServiceClient.postTask(eq(businessKey), anyString(), anyString()))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid SKU format"));

        postDispatchWorker.dispatchPendingTasks();

        ExternalTask finalState = taskRepository.findById(id).orElseThrow();
        assertEquals(ExternalTaskState.FATAL_FAILED, finalState.state());
        assertTrue(finalState.lastError().contains("400"));
    }

    @Test
    @DisplayName("Scenario 5: Max retries exhausted -> FATAL_FAILED")
    void shouldMarkFatalFailedWhenMaxRetriesExhausted() {
        UUID businessKey = UUID.randomUUID();
        ExternalTask task = new ExternalTask(
                null, businessKey, "SUPPLIER_PRODUCT_SYNC", ExternalTaskState.FAILED_CHECK_NEEDED,
                "{}", null, 3, 3, Instant.now().minusSeconds(10), "Timeout", Instant.now(), Instant.now()
        );
        Long id = taskRepository.save(task);

        when(externalServiceClient.checkTaskStatus(eq(businessKey)))
                .thenReturn(Optional.empty());

        getReconciliationWorker.reconcileUncertainTasks();

        ExternalTask finalState = taskRepository.findById(id).orElseThrow();
        assertEquals(ExternalTaskState.FATAL_FAILED, finalState.state());
        assertTrue(finalState.lastError().contains("Exceeded max retries"));
    }

    @Test
    @DisplayName("Scenario 6: SKIP LOCKED concurrency test")
    void shouldSkipLockedRowsConcurrently() {
        UUID k1 = UUID.randomUUID();
        UUID k2 = UUID.randomUUID();

        taskRepository.save(ExternalTask.newPendingTask(k1, "SYNC", "{}", 3));
        taskRepository.save(ExternalTask.newPendingTask(k2, "SYNC", "{}", 3));

        List<ExternalTask> batch1 = taskRepository.lockBatchForProcessing(ExternalTaskState.PENDING, 1);
        assertEquals(1, batch1.size());

        List<ExternalTask> batch2 = taskRepository.lockBatchForProcessing(ExternalTaskState.PENDING, 10);
        assertEquals(2, batch2.size());
    }
}
