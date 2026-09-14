package com.serjnn.ProductService.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifier")
public record NotifierProperties(
        int pageSize,
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity
) {
}
