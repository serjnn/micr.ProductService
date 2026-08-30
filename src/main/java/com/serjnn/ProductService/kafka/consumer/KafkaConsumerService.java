package com.serjnn.ProductService.kafka.consumer;

import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.services.IncomingDiscountsProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final IncomingDiscountsProcessor incomingDiscountsProcessor;

    @KafkaListener(topics = "${app.kafka.topic.discount-changes}", groupId = "${spring.kafka.consumer.group-id}")
    public void discountListener(List<DiscountChangesDto> discountChangesDtos) {
        log.info("Received {} discount changes from Kafka batch", discountChangesDtos.size());
        discountChangesDtos.forEach(incomingDiscountsProcessor::process);
    }
}
