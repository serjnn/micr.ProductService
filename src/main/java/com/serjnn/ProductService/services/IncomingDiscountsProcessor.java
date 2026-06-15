package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.mappers.DiscountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncomingDiscountsProcessor {
    private final DiscountService discountService;
    private final SubscribersNotifier subscribersNotifier;

    public void process(DiscountChangesDto discountChangesDto) {
        log.info("Processing incoming discount changes: {}", discountChangesDto);
        CacheableDiscountDto cacheableDiscountDto =
                DiscountMapper.INSTANCE.toCacheableDto(discountChangesDto);
        discountService.updateCache(cacheableDiscountDto);
        double newDiscount = discountChangesDto.newDiscount();
        double prevDiscount = discountChangesDto.prevDiscount();


        if (Double.compare(prevDiscount, newDiscount) < 0) {
            log.info("Discount increased for product {}. Notifying subscribers.", discountChangesDto.productId());
            subscribersNotifier.notifySubscribers(discountChangesDto);
        } else {
            log.info("Discount did not increase for product {}. No notification needed.", discountChangesDto.productId());
        }
    }
}
