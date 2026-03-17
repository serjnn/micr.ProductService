package com.serjnn.ProductService.services;

import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.models.Subscriber;
import com.serjnn.ProductService.redis.DiscountCacheManager;
import com.serjnn.ProductService.repo.ProductRepository;
import com.serjnn.ProductService.repo.SubscribersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final RestTemplate restTemplate;
    private final SubscribersRepository subscribersRepository;
    private final DiscountCacheManager discountCacheManager;

    public List<Product> findProductsByCategory(Category category) {
        List<Product> products = productRepository.findProductsByCategory(category);
        return countDiscount(products);
    }

    public List<Product> findAll() {
        List<Product> products = productRepository.findAll();
        return countDiscount(products);
    }

    public List<Product> findProductsByIds(IdsRequest idsRequest) {
        List<Long> ids = idsRequest.ids();
        List<Product> products = productRepository.findAllById(ids);
        return countDiscount(products);
    }

    private List<Product> countDiscount(List<Product> products) {
        return products.stream().map(product -> {
            Optional<CacheableDiscountDto> discountOpt = getDiscountByAnyCost(product.id());
            if (discountOpt.isPresent()) {
                BigDecimal discount = BigDecimal.valueOf(discountOpt.get().discount());

                BigDecimal newPrice = product.price()
                        .multiply(
                                BigDecimal.ONE.subtract(
                                        discount.divide(BigDecimal.valueOf(100))))
                        .setScale(2, RoundingMode.HALF_UP);
                return new Product(product.id(), product.name(), product.description(), newPrice, product.category());
            }
            return product;
        }).collect(Collectors.toList());
    }

    public Optional<CacheableDiscountDto> getDiscountByAnyCost(Long id) {
        Optional<CacheableDiscountDto> cached = discountCacheManager.getDiscountByProductId(id);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<CacheableDiscountDto> fetched = askDiscountService(id);
        if (fetched.isPresent()) {
            discountCacheManager.addToCache(fetched.get());
            return fetched;
        } else {
            discountCacheManager.addToCache(new CacheableDiscountDto(id, 0.0));
            return Optional.empty();
        }
    }

    private Optional<CacheableDiscountDto> askDiscountService(Long productId) {
        try {
            CacheableDiscountDto response = restTemplate.getForObject("http://discount/api/v1/byProductId/" + productId, CacheableDiscountDto.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Error while fetching discount for product " + productId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public void add(Product product) {
        productRepository.save(product);
    }

    public void subscribe(Long clientId, Long productId) {
        subscribersRepository.save(new Subscriber(null, productId, clientId));
    }
}
