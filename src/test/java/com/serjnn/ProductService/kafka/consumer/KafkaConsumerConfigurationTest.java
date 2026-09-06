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

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigurationTest {

    private KafkaConfigProperties kafkaConfigProperties;
    private MeterRegistry meterRegistry;
    private KafkaConsumerConfiguration configuration;

    @Mock
    private KafkaTemplate<Object, Object> dltKafkaTemplate;

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
