package com.serjnn.ProductService.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.services")
public record AppServicesProperties(
        String discountUrl
) {
}
