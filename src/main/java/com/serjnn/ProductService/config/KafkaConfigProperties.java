package com.serjnn.ProductService.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.kafka")
public record KafkaConfigProperties(
        String bootstrapServers,
        Consumer consumer,
        Producer producer
) {
    public record Consumer(
            String groupId,
            String autoOffsetReset,
            Boolean enableAutoCommit,
            Integer autoCommitInterval,
            String valueDefaultType,
            String trustedPackages,
            Integer maxPollRecords
    ) {
    }

    public record Producer(
            String acks
    ) {
    }
}
