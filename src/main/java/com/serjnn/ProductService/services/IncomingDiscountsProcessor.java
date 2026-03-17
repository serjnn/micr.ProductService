package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.mappers.DiscountMapper;
import com.serjnn.ProductService.redis.DiscountCacheManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IncomingDiscountsProcessor {
    private final DiscountCacheManager discountCacheManager;
    private final SubscribersNotifier subscribersNotifier;

    public void process(DiscountChangesDto discountChangesDto) {
        CacheableDiscountDto cacheableDiscountDto = DiscountMapper.INSTANCE.toCacheableDto(discountChangesDto);
        discountCacheManager.addToCache(cacheableDiscountDto);

        if (discountChangesDto.prevDiscount() == null) {
            return;
        }

        if (Double.compare(discountChangesDto.prevDiscount(),
                discountChangesDto.newDiscount()) < 0) {
            subscribersNotifier.notifySubscribers(discountChangesDto);
        }
    }
}
