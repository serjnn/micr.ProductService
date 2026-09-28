package com.serjnn.ProductService.externaltask.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.external-task")
public record ExternalTaskProperties(
        @DefaultValue("2000") long dispatchDelayMs,
        @DefaultValue("3000") long reconcileDelayMs,
        @DefaultValue("10") int batchSize,
        @DefaultValue("5") int defaultMaxRetries,
        @DefaultValue("5") long auditDelaySeconds,
        @DefaultValue("2") long baseBackoffSeconds,
        @DefaultValue("http://supplier/api/v1/products") String serviceUrl,
        @DefaultValue("3000") long connectTimeoutMs,
        @DefaultValue("3000") long readTimeoutMs
) {
}
