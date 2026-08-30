package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncomingDiscountsProcessorTest {

    @Mock
    private DiscountService discountService;

    @Mock
    private SubscribersNotifier subscribersNotifier;

    private IncomingDiscountsProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new IncomingDiscountsProcessor(discountService, subscribersNotifier);
    }

    @Test
    @DisplayName("Should update cache and notify subscribers when discount increases")
    void shouldNotifyWhenDiscountIncreases() {
        DiscountChangesDto dto = new DiscountChangesDto(10L, 25.0, 10.0);

        processor.process(dto);

        verify(discountService, times(1)).updateCache(any(CacheableDiscountDto.class));
        verify(subscribersNotifier, times(1)).notifySubscribers(dto);
    }

    @Test
    @DisplayName("Should update cache but NOT notify subscribers when discount decreases or stays the same")
    void shouldNotNotifyWhenDiscountDecreasesOrStaysSame() {
        DiscountChangesDto decreaseDto = new DiscountChangesDto(10L, 10.0, 20.0);
        DiscountChangesDto sameDto = new DiscountChangesDto(10L, 15.0, 15.0);

        processor.process(decreaseDto);
        processor.process(sameDto);

        verify(discountService, times(2)).updateCache(any(CacheableDiscountDto.class));
        verifyNoInteractions(subscribersNotifier);
    }
}
