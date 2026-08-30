package com.serjnn.ProductService.services;

import com.serjnn.ProductService.exceptions.DuplicateSubscriptionException;
import com.serjnn.ProductService.exceptions.ProductNotFoundException;
import com.serjnn.ProductService.exceptions.SubscriptionNotFoundException;
import com.serjnn.ProductService.models.Subscriber;
import com.serjnn.ProductService.repo.ProductRepository;
import com.serjnn.ProductService.repo.SubscribersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscribersRepository subscribersRepository;

    @Mock
    private ProductRepository productRepository;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(subscribersRepository, productRepository);
    }

    @Test
    @DisplayName("Should subscribe client to product when valid and not already subscribed")
    void shouldSubscribeClientSuccessfully() {
        Long productId = 10L;
        Long clientId = 100L;

        when(productRepository.existsById(productId)).thenReturn(true);
        when(subscribersRepository.existsByProductIdAndClientId(productId, clientId)).thenReturn(false);

        assertDoesNotThrow(() -> subscriptionService.subscribe(clientId, productId));

        ArgumentCaptor<Subscriber> captor = ArgumentCaptor.forClass(Subscriber.class);
        verify(subscribersRepository).save(captor.capture());
        assertEquals(productId, captor.getValue().productId());
        assertEquals(clientId, captor.getValue().clientId());
    }

    @Test
    @DisplayName("Should throw ProductNotFoundException when subscribing to non-existent product")
    void shouldThrowWhenProductDoesNotExist() {
        Long productId = 999L;
        Long clientId = 100L;

        when(productRepository.existsById(productId)).thenReturn(false);

        assertThrows(ProductNotFoundException.class, () -> subscriptionService.subscribe(clientId, productId));
        verify(subscribersRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DuplicateSubscriptionException when already subscribed")
    void shouldThrowWhenAlreadySubscribed() {
        Long productId = 10L;
        Long clientId = 100L;

        when(productRepository.existsById(productId)).thenReturn(true);
        when(subscribersRepository.existsByProductIdAndClientId(productId, clientId)).thenReturn(true);

        assertThrows(DuplicateSubscriptionException.class, () -> subscriptionService.subscribe(clientId, productId));
        verify(subscribersRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should unsubscribe client successfully")
    void shouldUnsubscribeSuccessfully() {
        Long productId = 10L;
        Long clientId = 100L;

        when(subscribersRepository.existsByProductIdAndClientId(productId, clientId)).thenReturn(true);

        assertDoesNotThrow(() -> subscriptionService.unsubscribe(clientId, productId));
        verify(subscribersRepository).deleteByProductIdAndClientId(productId, clientId);
    }

    @Test
    @DisplayName("Should throw SubscriptionNotFoundException when unsubscribing non-existent subscription")
    void shouldThrowWhenUnsubscribingNonExistent() {
        Long productId = 10L;
        Long clientId = 100L;

        when(subscribersRepository.existsByProductIdAndClientId(productId, clientId)).thenReturn(false);

        assertThrows(SubscriptionNotFoundException.class, () -> subscriptionService.unsubscribe(clientId, productId));
        verify(subscribersRepository, never()).deleteByProductIdAndClientId(any(), any());
    }

    @Test
    @DisplayName("Should get subscribed product IDs for client")
    void shouldGetSubscribedProductIds() {
        Long clientId = 100L;
        Pageable pageable = PageRequest.of(0, 10);
        when(subscribersRepository.findProductIdsByClientId(clientId, pageable))
                .thenReturn(new SliceImpl<>(List.of(1L, 2L), pageable, false));

        Slice<Long> result = subscriptionService.getSubscribedProductIds(clientId, pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(List.of(1L, 2L), result.getContent());
    }
}
