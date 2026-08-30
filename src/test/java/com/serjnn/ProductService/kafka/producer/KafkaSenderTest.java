package com.serjnn.ProductService.kafka.producer;

import com.serjnn.ProductService.dtos.DiscountNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaSenderTest {

    @Mock
    private KafkaTemplate<String, DiscountNotification> kafkaTemplate;

    private KafkaSender kafkaSender;

    @BeforeEach
    void setUp() {
        kafkaSender = new KafkaSender(kafkaTemplate);
    }

    @Test
    @DisplayName("Should send message to Kafka with product ID as partition key")
    void shouldSendMessageWithPartitionKey() {
        Long productId = 100L;
        Long clientId = 50L;
        DiscountNotification notification = new DiscountNotification(productId, clientId, 20.0);
        String topic = "discountNotifTopic";

        CompletableFuture<SendResult<String, DiscountNotification>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplate.send(eq(topic), eq(String.valueOf(productId)), eq(notification)))
                .thenReturn(future);

        CompletableFuture<SendResult<String, DiscountNotification>> result =
                kafkaSender.sendDiscountNotification(topic, notification);

        assertNotNull(result);
        verify(kafkaTemplate, times(1)).send(eq(topic), eq("100"), eq(notification));
    }
}
