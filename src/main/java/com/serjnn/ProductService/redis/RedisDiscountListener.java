package com.serjnn.ProductService.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.services.IncomingDiscountsProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDiscountListener implements MessageListener {

    private final DiscountCacheManager discountCacheManager;
    private final ObjectMapper objectMapper;

    private final IncomingDiscountsProcessor incomingDiscountsProcessor;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            log.info("Received message from Redis channel: {}", new String(message.getChannel()));
            
            DiscountChangesDto dto = objectMapper.readValue(message.getBody(), DiscountChangesDto.class);
            log.info("Updating product {} in cache with new discount: {}", dto.productId(), dto.newDiscount());
            
            incomingDiscountsProcessor.process(dto);

        } catch (IOException e) {
            log.error("Failed to parse Redis message", e);
        }
    }
}
