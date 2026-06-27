package com.serjnn.ProductService.kafka.producer;

import com.serjnn.ProductService.dtos.DiscountNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaSender {
    private final KafkaTemplate<String, DiscountNotification> kafkaTemplate;

    public Mono<Void> sendDiscountNotification(String topicName, DiscountNotification discountNotification) {
        log.info("Sending discount notification to topic {}: {}", topicName, discountNotification);
        return Mono.fromFuture(() -> kafkaTemplate.send(topicName, discountNotification))
                .doOnError(e -> log.error("Failed to send discount notification to topic {}: {}", topicName, discountNotification, e))
                .then();
    }
}
