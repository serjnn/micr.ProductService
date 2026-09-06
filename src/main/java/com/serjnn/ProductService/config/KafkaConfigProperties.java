package com.serjnn.ProductService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.kafka")
public class KafkaConfigProperties {
    private String bootstrapServers;
    private Consumer consumer = new Consumer();
    private Producer producer = new Producer();

    @Data
    public static class Consumer {
        private String groupId;
        private String autoOffsetReset;
        private Boolean enableAutoCommit;
        private Integer autoCommitInterval;
        private String valueDefaultType;
        private String trustedPackages;
        private Integer maxPollRecords;
    }

    @Data
    public static class Producer {
        private String acks;
    }
}
