package com.serjnn.ProductService.kafka.consumer;

import com.serjnn.ProductService.config.KafkaConfigProperties;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.client.HttpClientErrorException;

import java.util.concurrent.CompletableFuture;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigurationTest {

    private KafkaConfigProperties kafkaConfigProperties;
    private MeterRegistry meterRegistry;
    private KafkaConsumerConfiguration configuration;

    @Mock
    private KafkaTemplate<Object, Object> dltKafkaTemplate;

    @Mock
    private Consumer<?, ?> consumer;

    @Mock
    private MessageListenerContainer container;

    @BeforeEach
    void setUp() {
        kafkaConfigProperties = new KafkaConfigProperties();
        kafkaConfigProperties.setBootstrapServers("localhost:9092");
        kafkaConfigProperties.getConsumer().setGroupId("test-group");
        kafkaConfigProperties.getConsumer().setAutoOffsetReset("earliest");
        kafkaConfigProperties.getConsumer().setEnableAutoCommit(false);
        kafkaConfigProperties.getConsumer().setAutoCommitInterval(100);
        kafkaConfigProperties.getConsumer().setMaxPollRecords(50);
        kafkaConfigProperties.getConsumer().setTrustedPackages("*");
        kafkaConfigProperties.getConsumer().setValueDefaultType("com.serjnn.ProductService.dtos.DiscountChangesDto");

        meterRegistry = new SimpleMeterRegistry();
        configuration = new KafkaConsumerConfiguration(kafkaConfigProperties, meterRegistry);
    }

    @Test
    @DisplayName("Should configure Kafka error handler with non-retryable 4xx and IllegalArgumentException")
    void shouldConfigureErrorHandlerWithNonRetryableExceptions() {
        CommonErrorHandler handler = configuration.kafkaErrorHandler(dltKafkaTemplate);

        assertNotNull(handler);
        assertInstanceOf(DefaultErrorHandler.class, handler);

        DefaultErrorHandler defaultErrorHandler = (DefaultErrorHandler) handler;
        assertTrue(defaultErrorHandler.isAckAfterHandle());
    }

    @Test
    @DisplayName("Should immediately publish to DLT on non-retryable HttpClientErrorException without retry")
    void shouldImmediatelyPublishToDltOnNonRetryableHttpClientErrorException() {
        CommonErrorHandler handler = configuration.kafkaErrorHandler(dltKafkaTemplate);
        DefaultErrorHandler defaultErrorHandler = (DefaultErrorHandler) handler;
        ConsumerRecord<String, DiscountChangesDto> record =
                new ConsumerRecord<>("discountChangesTopic", 0, 10L, "1", new DiscountChangesDto(1L, 20.0, 10.0));
        HttpClientErrorException exception =
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);

        CompletableFuture<SendResult<Object, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(dltKafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        defaultErrorHandler.handleOne(exception, record, consumer, container);

        verify(dltKafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("Should immediately publish to DLT on non-retryable IllegalArgumentException without retry")
    void shouldImmediatelyPublishToDltOnNonRetryableIllegalArgumentException() {
        CommonErrorHandler handler = configuration.kafkaErrorHandler(dltKafkaTemplate);
        DefaultErrorHandler defaultErrorHandler = (DefaultErrorHandler) handler;
        ConsumerRecord<String, DiscountChangesDto> record =
                new ConsumerRecord<>("discountChangesTopic", 0, 10L, "1", new DiscountChangesDto(1L, 20.0, 10.0));
        IllegalArgumentException exception = new IllegalArgumentException("Invalid discount rate");

        CompletableFuture<SendResult<Object, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(dltKafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        defaultErrorHandler.handleOne(exception, record, consumer, container);

        verify(dltKafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("Should not immediately publish to DLT on transient exception")
    void shouldNotImmediatelyPublishToDltOnTransientException() {
        CommonErrorHandler handler = configuration.kafkaErrorHandler(dltKafkaTemplate);
        DefaultErrorHandler defaultErrorHandler = (DefaultErrorHandler) handler;
        ConsumerRecord<String, DiscountChangesDto> record =
                new ConsumerRecord<>("discountChangesTopic", 0, 10L, "1", new DiscountChangesDto(1L, 20.0, 10.0));
        RuntimeException transientException = new RuntimeException("Transient DB timeout");

        defaultErrorHandler.handleOne(transientException, record, consumer, container);

        verify(dltKafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("Should publish to DLT when retries are exhausted")
    void shouldPublishToDltWhenRetriesAreExhausted() {
        org.springframework.kafka.listener.DeadLetterPublishingRecoverer recoverer =
                new org.springframework.kafka.listener.DeadLetterPublishingRecoverer(dltKafkaTemplate);
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new org.springframework.util.backoff.FixedBackOff(0L, 2L));

        ConsumerRecord<String, DiscountChangesDto> record =
                new ConsumerRecord<>("discountChangesTopic", 0, 10L, "1", new DiscountChangesDto(1L, 20.0, 10.0));
        RuntimeException transientException = new RuntimeException("Transient DB timeout");

        CompletableFuture<SendResult<Object, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(dltKafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Attempt 1 and 2 (retries)
        errorHandler.handleOne(transientException, record, consumer, container);
        errorHandler.handleOne(transientException, record, consumer, container);
        verify(dltKafkaTemplate, never()).send(any(ProducerRecord.class));

        // Attempt 3 (2 retries exhausted) -> Publishes to DLT
        errorHandler.handleOne(transientException, record, consumer, container);
        verify(dltKafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("Should configure listener container factory with single record and RECORD ack mode")
    void shouldConfigureListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DiscountChangesDto> factory =
                configuration.kafkaListenerContainerFactory(dltKafkaTemplate);

        assertNotNull(factory);
        assertFalse(factory.isBatchListener());
        assertEquals(ContainerProperties.AckMode.RECORD, factory.getContainerProperties().getAckMode());
    }

    @Test
    @DisplayName("Should create consumer factory")
    void shouldCreateConsumerFactory() {
        ConsumerFactory<String, DiscountChangesDto> consumerFactory = configuration.consumerFactory();
        assertNotNull(consumerFactory);
    }
}
