package com.serjnn.ProductService.externaltask.dto;

import java.util.UUID;

public record ExternalServiceRequest(
        UUID businessKey,
        String taskType,
        String payload
) {}
