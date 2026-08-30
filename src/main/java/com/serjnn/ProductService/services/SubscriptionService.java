package com.serjnn.ProductService.services;

import com.serjnn.ProductService.exceptions.DuplicateSubscriptionException;
import com.serjnn.ProductService.exceptions.ProductNotFoundException;
import com.serjnn.ProductService.exceptions.SubscriptionNotFoundException;
import com.serjnn.ProductService.models.Subscriber;
import com.serjnn.ProductService.repo.ProductRepository;
import com.serjnn.ProductService.repo.SubscribersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SubscriptionService {
    private final SubscribersRepository subscribersRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void subscribe(Long clientId, Long productId) {
        log.info("Subscribing client {} to product {}", clientId, productId);
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        if (subscribersRepository.existsByProductIdAndClientId(productId, clientId)) {
            throw new DuplicateSubscriptionException(productId, clientId);
        }
        subscribersRepository.save(new Subscriber(null, productId, clientId));
    }

    @Transactional
    public void unsubscribe(Long clientId, Long productId) {
        log.info("Unsubscribing client {} from product {}", clientId, productId);
        if (!subscribersRepository.existsByProductIdAndClientId(productId, clientId)) {
            throw new SubscriptionNotFoundException(productId, clientId);
        }
        subscribersRepository.deleteByProductIdAndClientId(productId, clientId);
    }

    public Slice<Long> getSubscribedProductIds(Long clientId, Pageable pageable) {
        log.info("Fetching subscribed product IDs for client {}", clientId);
        return subscribersRepository.findProductIdsByClientId(clientId, pageable);
    }

    public Slice<Long> getSubscriberClientIds(Long productId, Pageable pageable) {
        log.info("Fetching subscriber client IDs for product {}", productId);
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return subscribersRepository.findClientIdsByProductId(productId, pageable);
    }
}
