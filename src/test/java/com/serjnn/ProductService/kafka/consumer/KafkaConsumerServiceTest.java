package com.serjnn.ProductService.kafka.consumer;

import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.services.IncomingDiscountsProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private IncomingDiscountsProcessor incomingDiscountsProcessor;

    private KafkaConsumerService kafkaConsumerService;

    @BeforeEach
    void setUp() {
        kafkaConsumerService = new KafkaConsumerService(incomingDiscountsProcessor);
    }

    @Test
    @DisplayName("Should process each message in batch")
    void shouldProcessBatchOfDiscountChanges() {
        DiscountChangesDto d1 = new DiscountChangesDto(1L, 20.0, 10.0);
        DiscountChangesDto d2 = new DiscountChangesDto(2L, 30.0, 15.0);

        kafkaConsumerService.discountListener(List.of(d1, d2));

        verify(incomingDiscountsProcessor, times(1)).process(d1);
        verify(incomingDiscountsProcessor, times(1)).process(d2);
    }
}
