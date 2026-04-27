package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.DiscountNotification;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.repo.SubscribersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscribersNotifier {
    private final SubscribersRepository subscribersRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.redis.channel.discount-notifications}")
    private String discountNotifChannel;

    public void notifySubscribers(DiscountChangesDto discountChangesDto) {
        log.info("Starting notification process for product {}. Data: {}", discountChangesDto.productId(), discountChangesDto);
        Long productId = discountChangesDto.productId();
        
        int pageSize = 100;
        Pageable pageable = PageRequest.of(0, pageSize);
        Slice<Long> clientIdsSlice;
        int totalNotified = 0;
        
        do {
            clientIdsSlice = subscribersRepository.findClientIdsByProductId(productId, pageable);
            List<Long> clientIds = clientIdsSlice.getContent();
            log.info("Found {} subscribers for product {} in current slice (page: {})", clientIds.size(), productId, pageable.getPageNumber());
            
            clientIds.forEach(clientId -> {
                DiscountNotification notification = new DiscountNotification(
                        discountChangesDto.productId(),
                        clientId,
                        discountChangesDto.newDiscount()
                );
                log.info("NOTIFYING SUBSCRIBER {} for product {}", notification.clientId(), notification.productId());
                redisTemplate.convertAndSend(discountNotifChannel, notification);
            });
            totalNotified += clientIds.size();
            pageable = pageable.next();
        } while (clientIdsSlice.hasNext());
        
        log.info("Notification process finished. Total subscribers notified: {}", totalNotified);
    }
}
