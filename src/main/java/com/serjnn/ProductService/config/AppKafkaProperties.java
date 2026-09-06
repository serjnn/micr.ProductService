package com.serjnn.ProductService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.kafka")
public class AppKafkaProperties {
    private Topic topic = new Topic();

    @Data
    public static class Topic {
        private String discountNotifications;
        private int discountNotificationsPartitions;
        private String discountChanges;
    }
}
