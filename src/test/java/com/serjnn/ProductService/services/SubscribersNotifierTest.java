package com.serjnn.ProductService.services;

import com.serjnn.ProductService.config.NotifierProperties;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.dtos.DiscountNotification;
import com.serjnn.ProductService.kafka.producer.KafkaSender;
import com.serjnn.ProductService.repo.SubscribersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.kafka.support.SendResult;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscribersNotifierTest {

    @Mock
    private SubscribersRepository subscribersRepository;

    @Mock
    private KafkaSender kafkaSender;

    private NotifierProperties notifierProperties;
    private com.serjnn.ProductService.config.AppKafkaProperties appKafkaProperties;

    // Use a direct executor so that the task runs synchronously in the test thread
    private final Executor directExecutor = Runnable::run;

    private SubscribersNotifier subscribersNotifier;

    @BeforeEach
    void setUp() {
        notifierProperties = new NotifierProperties(2, 10, 50, 10000);
        appKafkaProperties = new com.serjnn.ProductService.config.AppKafkaProperties(
                new com.serjnn.ProductService.config.AppKafkaProperties.Topic("test-discount-notifications", 3, "changes")
        );

        subscribersNotifier = new SubscribersNotifier(
                subscribersRepository,
                kafkaSender,
                notifierProperties,
                appKafkaProperties,
                directExecutor
        );
    }

    @Test
    void shouldNotifySubscribersInSlicesAndJoinFutures() {
        // Arrange
        Long productId = 100L;
        DiscountChangesDto changes = new DiscountChangesDto(productId, 20.0, 10.0);

        Pageable page1 = PageRequest.of(0, 2);
        Pageable page2 = PageRequest.of(1, 2);

        List<Long> clientsPage1 = List.of(1L, 2L);
        List<Long> clientsPage2 = List.of(3L);

        Slice<Long> slice1 = new SliceImpl<>(clientsPage1, page1, true);
        Slice<Long> slice2 = new SliceImpl<>(clientsPage2, page2, false);

        when(subscribersRepository.findClientIdsByProductId(eq(productId), eq(page1))).thenReturn(slice1);
        when(subscribersRepository.findClientIdsByProductId(eq(productId), eq(page2))).thenReturn(slice2);

        // Mock sender to return completed future
        CompletableFuture<SendResult<String, DiscountNotification>> mockFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaSender.sendDiscountNotification(eq("test-discount-notifications"), any(DiscountNotification.class)))
                .thenReturn(mockFuture);

        // Act
        subscribersNotifier.notifySubscribers(changes);

        // Assert
        verify(subscribersRepository, times(1)).findClientIdsByProductId(productId, page1);
        verify(subscribersRepository, times(1)).findClientIdsByProductId(productId, page2);

        ArgumentCaptor<DiscountNotification> notificationCaptor = ArgumentCaptor.forClass(DiscountNotification.class);
        verify(kafkaSender, times(3)).sendDiscountNotification(eq("test-discount-notifications"), notificationCaptor.capture());

        List<DiscountNotification> sentNotifications = notificationCaptor.getAllValues();
        assertEquals(3, sentNotifications.size());

        assertEquals(1L, sentNotifications.get(0).clientId());
        assertEquals(productId, sentNotifications.get(0).productId());
        assertEquals(20.0, sentNotifications.get(0).discount());

        assertEquals(2L, sentNotifications.get(1).clientId());
        assertEquals(3L, sentNotifications.get(2).clientId());
    }

    @Test
    void shouldDoNothingIfNoSubscribers() {
        // Arrange
        Long productId = 200L;
        DiscountChangesDto changes = new DiscountChangesDto(productId, 15.0, 5.0);
        Pageable page1 = PageRequest.of(0, 2);
        Slice<Long> emptySlice = new SliceImpl<>(Collections.emptyList(), page1, false);

        when(subscribersRepository.findClientIdsByProductId(eq(productId), eq(page1))).thenReturn(emptySlice);

        // Act
        subscribersNotifier.notifySubscribers(changes);

        // Assert
        verify(subscribersRepository, times(1)).findClientIdsByProductId(productId, page1);
        verifyNoInteractions(kafkaSender);
    }
}
