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
    @DisplayName("Should process discount change message")
    void shouldProcessDiscountChange() {
        DiscountChangesDto dto = new DiscountChangesDto(1L, 20.0, 10.0);

        kafkaConsumerService.discountListener(dto);

        verify(incomingDiscountsProcessor, times(1)).process(dto);
    }
}
