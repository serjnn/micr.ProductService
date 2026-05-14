package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.DiscountResponseDto;
import com.serjnn.ProductService.dtos.IdsRequest;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountClient {

    private final RestTemplate restTemplate;
    @Value("${app.services.discount-url}")
    private String discountUrl;

    @Retry(name = "discountService", fallbackMethod = "getDiscountFallback")
    public Optional<DiscountResponseDto> callDiscountService(Long productId) {
        DiscountResponseDto response =
                restTemplate.getForObject(discountUrl + productId, DiscountResponseDto.class);
        return Optional.ofNullable(response);
    }

    public List<DiscountResponseDto> callDiscountServiceBatch(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Collections.emptyList();
        
        DiscountResponseDto[] response = restTemplate.postForObject(
                discountUrl + "batch", 
                new IdsRequest(productIds), 
                DiscountResponseDto[].class);
        
        return response != null ? Arrays.asList(response) : Collections.emptyList();
    }

    public Optional<DiscountResponseDto> getDiscountFallback(Long id, Exception e) {
        log.error(
                "Error fetching discount for product {} after retries: {}. Using fallback (0.0 discount).",
                id, e.getMessage());
        return Optional.of(new DiscountResponseDto(id, 0.0));
    }

}
