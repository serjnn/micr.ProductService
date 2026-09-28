package com.serjnn.ProductService.externaltask.controller;

import com.serjnn.ProductService.externaltask.dto.CreateExternalTaskRequest;
import com.serjnn.ProductService.externaltask.dto.ExternalTaskResponse;
import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.service.ExternalTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/external-tasks")
@Tag(name = "External Task Controller", description = "RESTful APIs for managing asynchronous external non-idempotent tasks with state machine reconciliation")
@Slf4j
@Validated
public class ExternalTaskController {

    private final ExternalTaskService externalTaskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a new external task", description = "Enqueues a new external non-idempotent task into the PostgreSQL state machine in PENDING state")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "External task successfully submitted"),
            @ApiResponse(responseCode = "400", description = "Invalid task request data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ExternalTaskResponse submitTask(@Valid @RequestBody CreateExternalTaskRequest request) {
        log.info("Received request to submit external task of type: {}", request.taskType());
        return externalTaskService.submitTask(request);
    }

    @GetMapping("/{businessKey}")
    @Operation(summary = "Get external task by business key", description = "Retrieve current state and audit info for an external task by its business UUID key")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "External task found and returned"),
            @ApiResponse(responseCode = "400", description = "Invalid business key supplied"),
            @ApiResponse(responseCode = "404", description = "External task not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ExternalTaskResponse getByBusinessKey(
            @Parameter(description = "External task unique business UUID key", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("businessKey") UUID businessKey) {
        log.info("Received request to get external task by business key: {}", businessKey);
        return externalTaskService.getTaskByBusinessKey(businessKey);
    }

    @GetMapping("/id/{id}")
    @Operation(summary = "Get external task by ID", description = "Retrieve current state for an external task by internal numeric ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "External task found and returned"),
            @ApiResponse(responseCode = "400", description = "Invalid task ID supplied"),
            @ApiResponse(responseCode = "404", description = "External task not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ExternalTaskResponse getById(
            @Parameter(description = "External task internal ID", example = "1")
            @PathVariable("id") @Positive Long id) {
        log.info("Received request to get external task by ID: {}", id);
        return externalTaskService.getTaskById(id);
    }

    @GetMapping
    @Operation(summary = "Get external tasks slice", description = "Retrieve a paginated slice of external tasks, optionally filtered by state")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "External tasks successfully retrieved"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Slice<ExternalTaskResponse> getTasks(
            @Parameter(description = "Optional filter by task state", example = "PENDING")
            @RequestParam(name = "state", required = false) ExternalTaskState state,
            Pageable pageable) {
        log.info("Received request to get external tasks slice with state filter: {}", state);
        return externalTaskService.getTasks(state, pageable);
    }
}
