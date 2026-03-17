package com.serjnn.ProductService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.repo.ProductRepository;
import com.serjnn.ProductService.repo.SubscribersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.redis.testcontainers.RedisContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class ProductServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getFirstMappedPort());
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SubscribersRepository subscribersRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() {
        // No need to clear DB manually if using @Transactional, but since we use JdbcTemplate directly, 
        // we might want to ensure a clean state if needed. 
        // For simplicity in this example, we'll just assume a clean state or use unique data.
    }

    @Test
    void shouldCreateProductAndRetrieveWithDiscount() throws Exception {
        // 1. Create a product
        Product product = new Product(null, "iPhone 15", "Latest model", new BigDecimal("1000.00"),
                Category.ELECTRONICS);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated());

        // 2. Mock Discount Service response
        CacheableDiscountDto discountDto = new CacheableDiscountDto(1L, 10.0); // 10% discount
        when(restTemplate.getForObject(anyString(), eq(CacheableDiscountDto.class)))
                .thenReturn(discountDto);

        // 3. Retrieve all products and verify price is discounted
        // Note: price was 1000, 10% discount -> 900
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.name == 'iPhone 15')].price").value(900.0));
    }

    @Test
    void shouldSubscribeToProduct() throws Exception {
        // 1. Create product first
        productRepository.save(new Product(null, "Laptop", "Workstation", new BigDecimal("2000.00"),
                Category.ELECTRONICS));
        List<Product> products = productRepository.findAll();
        Long productId = products.get(0).id();

        // 2. Subscribe
        mockMvc.perform(post("/api/v1/products/" + productId + "/subscribe/123"))
                .andExpect(status().isCreated());

        // 3. Verify in DB
        List<Long> subscriberIds = subscribersRepository.findClientIdsByProductId(productId);
        assert (subscriberIds.contains(123L));
    }

    @Test
    void shouldGetProductsByIds() throws Exception {
        productRepository.save(new Product(null, "Book", "Novel", new BigDecimal("20.00"),
                Category.TOYS));
        List<Product> products = productRepository.findAll();
        Long productId = products.get(products.size() - 1).id();

        IdsRequest request = new IdsRequest(List.of(productId));

        mockMvc.perform(post("/api/v1/products/by-ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Book"));
    }
}
