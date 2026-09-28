package com.serjnn.ProductService.externaltask.dto;

import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;
import com.serjnn.ProductService.externaltask.models.ExternalTask;

import java.time.Instant;
import java.util.UUID;

public record ExternalTaskResponse(
        Long id,
        UUID businessKey,
        String taskType,
        ExternalTaskState state,
        String payload,
        String externalResourceId,
        int retryCount,
        int maxRetries,
        Instant nextRetryAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExternalTaskResponse from(ExternalTask task) {
        if (task == null) {
            return null;
        }
        return new ExternalTaskResponse(
                task.id(),
                task.businessKey(),
                task.taskType(),
                task.state(),
                task.payload(),
                task.externalResourceId(),
                task.retryCount(),
                task.maxRetries(),
                task.nextRetryAt(),
                task.lastError(),
                task.createdAt(),
                task.updatedAt()
        );
    }
}
