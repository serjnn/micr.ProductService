package com.serjnn.ProductService.externaltask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateExternalTaskRequest(
        @NotBlank
        @Size(max = 50)
        String taskType,

        @NotBlank
        String payload,

        @Positive
        Integer maxRetries
) {}
