package com.serjnn.ProductService.externaltask.models;

import com.serjnn.ProductService.externaltask.enums.ExternalTaskState;

import java.time.Instant;
import java.util.UUID;

public record ExternalTask(
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
    public static ExternalTask newPendingTask(UUID businessKey, String taskType, String payload, int maxRetries) {
        Instant now = Instant.now();
        return new ExternalTask(
                null,
                businessKey,
                taskType,
                ExternalTaskState.PENDING,
                payload,
                null,
                0,
                maxRetries,
                now,
                null,
                now,
                now
        );
    }
}
