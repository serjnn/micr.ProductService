package com.serjnn.ProductService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.notifier")
public class NotifierProperties {
    /**
     * Number of subscribers fetched in each slice/page from the database.
     */
    private int pageSize = 100;

    /**
     * Core pool size for the notifier's task executor.
     */
    private int corePoolSize = 10;

    /**
     * Max pool size for the notifier's task executor.
     */
    private int maxPoolSize = 50;

    /**
     * Queue capacity for the notifier's task executor.
     */
    private int queueCapacity = 10000;
}
