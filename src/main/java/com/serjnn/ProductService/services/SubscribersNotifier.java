package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.DiscountNotification;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.kafka.kafkaProducer.KafkaSender;
import com.serjnn.ProductService.repo.SubscribersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscribersNotifier {
    private final SubscribersRepository subscribersRepository;
    private final KafkaSender kafkaSender;

    @Value("${app.kafka.topic.discount-notifications}")
    private String discountNotifTopic;

    public void notifySubscribers(DiscountChangesDto discountChangesDto) {
        log.info("notifying subscribers " + discountChangesDto);
        Long productId = discountChangesDto.productId();
        
        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Slice<Long> clientIdsSlice;
        
        do {
            clientIdsSlice = subscribersRepository.findClientIdsByProductId(productId, pageable);
            clientIdsSlice.getContent().forEach(clientId -> {
                DiscountNotification notification = new DiscountNotification(
                        discountChangesDto.productId(),
                        clientId,
                        discountChangesDto.newDiscount()
                );
                kafkaSender.sendDiscountNotification(discountNotifTopic, notification);
            });
            pageable = pageable.next();
        } while (clientIdsSlice.hasNext());
    }
}
