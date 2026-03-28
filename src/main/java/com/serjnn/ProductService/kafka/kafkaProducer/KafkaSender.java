package com.serjnn.ProductService.kafka.kafkaProducer;

import com.serjnn.ProductService.dtos.DiscountNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaSender {
    private final KafkaTemplate<String, DiscountNotification> kafkaTemplate;

    public void sendDiscountNotification(String topicName, DiscountNotification discountNotification) {
        log.info("Sending discount notification to topic {}: {}", topicName, discountNotification);
        kafkaTemplate.send(topicName, discountNotification);
    }
}
