package com.serjnn.ProductService.externaltask.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serjnn.ProductService.exceptions.GlobalExceptionHandler;
import com.serjnn.ProductService.externaltask.dto.CreateExternalTaskRequest;
import com.serjnn.ProductService.externaltask.dto.ExternalTaskResponse;
import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.exception.ExternalTaskNotFoundException;
import com.serjnn.ProductService.externaltask.service.ExternalTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExternalTaskController.class)
@Import(GlobalExceptionHandler.class)
class ExternalTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExternalTaskService externalTaskService;

    @Test
    @DisplayName("POST /api/v1/external-tasks - Should submit task and return 201 Created")
    void shouldSubmitTask() throws Exception {
        UUID businessKey = UUID.randomUUID();
        CreateExternalTaskRequest request = new CreateExternalTaskRequest("SUPPLIER_CATALOG_SYNC", "{\"sku\":\"PROD-1\"}", 5);
        ExternalTaskResponse response = new ExternalTaskResponse(
                1L, businessKey, "SUPPLIER_CATALOG_SYNC", ExternalTaskState.PENDING,
                "{\"sku\":\"PROD-1\"}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now()
        );

        when(externalTaskService.submitTask(any(CreateExternalTaskRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/external-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.businessKey").value(businessKey.toString()))
                .andExpect(jsonPath("$.taskType").value("SUPPLIER_CATALOG_SYNC"))
                .andExpect(jsonPath("$.state").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/external-tasks/{businessKey} - Should return 200 and task details")
    void shouldGetByBusinessKey() throws Exception {
        UUID businessKey = UUID.randomUUID();
        ExternalTaskResponse response = new ExternalTaskResponse(
                1L, businessKey, "SUPPLIER_CATALOG_SYNC", ExternalTaskState.SUCCEEDED,
                "{\"sku\":\"PROD-1\"}", "EXT-RESP-123", 0, 5, Instant.now(), null, Instant.now(), Instant.now()
        );

        when(externalTaskService.getTaskByBusinessKey(businessKey)).thenReturn(response);

        mockMvc.perform(get("/api/v1/external-tasks/{businessKey}", businessKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.businessKey").value(businessKey.toString()))
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.externalResourceId").value("EXT-RESP-123"));
    }

    @Test
    @DisplayName("GET /api/v1/external-tasks/{businessKey} - Should return 404 ProblemDetail when task not found")
    void shouldReturn404WhenNotFound() throws Exception {
        UUID businessKey = UUID.randomUUID();
        when(externalTaskService.getTaskByBusinessKey(businessKey))
                .thenThrow(new ExternalTaskNotFoundException(businessKey));

        mockMvc.perform(get("/api/v1/external-tasks/{businessKey}", businessKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("External Task Not Found"))
                .andExpect(jsonPath("$.businessKey").value(businessKey.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/external-tasks - Should return paginated slice")
    void shouldGetTasksSlice() throws Exception {
        UUID businessKey = UUID.randomUUID();
        ExternalTaskResponse response = new ExternalTaskResponse(
                1L, businessKey, "SUPPLIER_CATALOG_SYNC", ExternalTaskState.PENDING,
                "{}", null, 0, 5, Instant.now(), null, Instant.now(), Instant.now()
        );

        when(externalTaskService.getTasks(eq(ExternalTaskState.PENDING), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(response), PageRequest.of(0, 10), false));

        mockMvc.perform(get("/api/v1/external-tasks")
                        .param("state", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].state").value("PENDING"));
    }
}
