package com.serjnn.ProductService.services;

import com.serjnn.ProductService.config.AppKafkaProperties;
import com.serjnn.ProductService.config.NotifierProperties;
import com.serjnn.ProductService.dtos.DiscountNotification;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.kafka.producer.KafkaSender;
import com.serjnn.ProductService.repo.SubscribersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class SubscribersNotifier {
    private final SubscribersRepository subscribersRepository;
    private final KafkaSender kafkaSender;
    private final NotifierProperties notifierProperties;
    private final AppKafkaProperties appKafkaProperties;
    private final Executor notifierTaskExecutor;

    public SubscribersNotifier(SubscribersRepository subscribersRepository,
                               KafkaSender kafkaSender,
                               NotifierProperties notifierProperties,
                               AppKafkaProperties appKafkaProperties,
                               @Qualifier("notifierTaskExecutor") Executor notifierTaskExecutor) {
        this.subscribersRepository = subscribersRepository;
        this.kafkaSender = kafkaSender;
        this.notifierProperties = notifierProperties;
        this.appKafkaProperties = appKafkaProperties;
        this.notifierTaskExecutor = notifierTaskExecutor;
    }

    public void notifySubscribers(DiscountChangesDto discountChangesDto) {
        notifierTaskExecutor.execute(() -> {
            log.info("Notifying subscribers: {}", discountChangesDto);
            Long productId = discountChangesDto.productId();
            int pageSize = notifierProperties.pageSize();
            Pageable pageable = PageRequest.of(0, pageSize);
            Slice<Long> clientIdsSlice;

            do {
                clientIdsSlice = subscribersRepository.findClientIdsByProductId(productId, pageable);
                List<CompletableFuture<SendResult<String, DiscountNotification>>> futures = clientIdsSlice.getContent().stream()
                        .map(clientId -> {
                            DiscountNotification notification = new DiscountNotification(
                                    productId,
                                    clientId,
                                    discountChangesDto.newDiscount()
                            );
                            return kafkaSender.sendDiscountNotification(appKafkaProperties.topic().discountNotifications(), notification);
                        })
                        .toList();

                if (!futures.isEmpty()) {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .whenComplete((v, ex) -> {
                                if (ex != null) {
                                    log.error("Failed to send a batch of discount notifications to Kafka", ex);
                                } else {
                                    log.debug("Successfully sent a batch of {} discount notifications to Kafka", futures.size());
                                }
                            })
                            .join();
                }

                pageable = pageable.next();
            } while (clientIdsSlice.hasNext());
        });
    }
}
