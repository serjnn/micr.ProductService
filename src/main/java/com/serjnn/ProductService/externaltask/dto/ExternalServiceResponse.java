package com.serjnn.ProductService.externaltask.dto;

public record ExternalServiceResponse(
        String externalResourceId,
        String status,
        String message
) {}
