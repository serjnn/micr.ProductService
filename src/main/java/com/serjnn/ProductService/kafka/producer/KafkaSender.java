package com.serjnn.ProductService.kafka.producer;

import com.serjnn.ProductService.dtos.DiscountNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaSender {
    private final KafkaTemplate<String, DiscountNotification> kafkaTemplate;

    public CompletableFuture<SendResult<String, DiscountNotification>> sendDiscountNotification(String topicName, DiscountNotification discountNotification) {
        String messageKey = String.valueOf(discountNotification.productId());
        log.info("Sending discount notification to topic {} with key {}: {}", topicName, messageKey, discountNotification);
        return kafkaTemplate.send(topicName, messageKey, discountNotification)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send discount notification to topic {}: {}", topicName, discountNotification, ex);
                    } else {
                        log.debug("Successfully sent discount notification to topic {}: {}", topicName, discountNotification);
                    }
                });
    }
}
