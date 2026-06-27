package com.serjnn.ProductService.kafka.consumer;


import com.serjnn.ProductService.service.SubscribersNotifier;
import com.serjnn.ProductService.dto.DiscountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final SubscribersNotifier subscribersNotifier;

    @KafkaListener(topics = "newDiscountTopic", groupId = "first_product_group")
    public void newDiscountsListener(DiscountDto discountDto) {
        log.info("Received new discount event from Kafka: {}", discountDto);
        subscribersNotifier.notifySubscribers(discountDto)
                .doOnError(err -> log.error("Failed to notify subscribers for discount event: {}", discountDto, err))
                .block(); // Block here to ensure the synchronous Kafka message listener thread waits for processing to complete before committing offsets
    }
}
