package com.serjnn.ProductService.services;

import com.serjnn.ProductService.models.Subscriber;
import com.serjnn.ProductService.repo.SubscribersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    private final SubscribersRepository subscribersRepository;

    public void subscribe(Long clientId, Long productId) {
        log.info("Subscribing client {} to product {}", clientId, productId);
        subscribersRepository.save(new Subscriber(null, productId, clientId));
    }
}
