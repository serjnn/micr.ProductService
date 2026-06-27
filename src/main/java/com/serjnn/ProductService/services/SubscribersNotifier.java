package com.serjnn.ProductService.services;


import com.serjnn.ProductService.dtos.DiscountDto;
import com.serjnn.ProductService.dtos.DiscountNotification;
import com.serjnn.ProductService.kafka.kafkaProducer.KafkaSender;
import com.serjnn.ProductService.repo.SubscribersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribersNotifier {
    private final SubscribersRepository subscribersRepository;
    private final KafkaSender kafkaSender;

    public Mono<Void> notifySubscribers(DiscountDto discountDto) {
        Long productId = discountDto.getProductId();
        log.info("Notifying subscribers for product ID: {} with discount: {}%", productId, discountDto.getDiscount());

        return subscribersRepository.findClientIdsByProductId(productId)
                .map(clientId -> new DiscountNotification(productId, clientId, discountDto.getDiscount()))
                .flatMap(dto -> kafkaSender.sendDiscountNotification("discountNotifTopic", dto))
                .then();
    }
}
