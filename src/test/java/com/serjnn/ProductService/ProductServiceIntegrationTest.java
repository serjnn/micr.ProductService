package com.serjnn.ProductService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serjnn.ProductService.dtos.CacheableDiscountDto;
import com.serjnn.ProductService.dtos.DiscountChangesDto;
import com.serjnn.ProductService.dtos.DiscountNotification;
import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.kafka.kafkaProducer.KafkaSender;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.repo.ProductRepository;
import com.serjnn.ProductService.repo.SubscribersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
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
import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

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
        registry.add("spring.datasource.url", () -> {
            String url = postgres.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=product_schema";
        });
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.serjnn.ProductService.redis.DiscountCacheManager discountCacheManager;

    @MockBean
    private RestTemplate restTemplate;

    @SpyBean
    private KafkaSender kafkaSender;

    @Autowired
    private KafkaTemplate kafkaTemplate;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("TRUNCATE TABLE subscribers RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE product RESTART IDENTITY CASCADE");
        discountCacheManager.clearCache();
    }

    @Test
    void shouldCreateProductAndRetrieveWithDiscount() throws Exception {
        // 1. Create a product
        Product product = new Product(null, "iPhone 15", "Latest model", new BigDecimal("1000.00"),
                Category.ELECTRONICS);

        String response = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        
        Long productId = Long.parseLong(response);
        assertNotNull(productId);

        // 2. Mock Discount Service response
        CacheableDiscountDto discountDto = new CacheableDiscountDto(productId, 10.0); // 10% discount
        when(restTemplate.getForObject(anyString(), eq(CacheableDiscountDto.class)))
                .thenReturn(discountDto);

        // 3. Retrieve all products and verify price is discounted
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[?(@.name == 'iPhone 15')].price").value(900.0));
    }

    @Test
    void shouldSubscribeToProduct() throws Exception {
        // 1. Create product first
        productRepository.save(new Product(null, "Laptop", "Workstation", new BigDecimal("2000.00"),
                Category.ELECTRONICS));
        Slice<Product> products = productRepository.findAll(PageRequest.of(0, 10));
        Long productId = products.getContent().get(0).id();

        // 2. Subscribe
        mockMvc.perform(post("/api/v1/products/" + productId + "/subscribe/123"))
                .andExpect(status().isCreated());

        // 3. Verify in DB
        Slice<Long> subscriberIds = subscribersRepository.findClientIdsByProductId(productId, PageRequest.of(0, 10));
        assertTrue(subscriberIds.getContent().contains(123L));
    }

    @Test
    void shouldGetProductsByIds() throws Exception {
        productRepository.save(new Product(null, "Book", "Novel", new BigDecimal("20.00"),
                Category.TOYS));
        Slice<Product> products = productRepository.findAll(PageRequest.of(0, 100));
        Long productId = products.getContent().get(products.getContent().size() - 1).id();

        IdsRequest request = new IdsRequest(List.of(productId));

        mockMvc.perform(post("/api/v1/products/by-ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Book"));
    }

    @Test
    void shouldProcessDiscountChangeAndNotifySubscribers() throws Exception {
        // 1. Create product and subscriber
        productRepository.save(new Product(null, "Kafka Product", "Kafka Desc", new BigDecimal("100.00"), Category.ELECTRONICS));
        Slice<Product> products = productRepository.findAll(PageRequest.of(0, 10));
        Long productId = products.getContent().get(0).id();

        mockMvc.perform(post("/api/v1/products/" + productId + "/subscribe/999"))
                .andExpect(status().isCreated());

        // 2. Prepare Kafka message for discount change (10% -> 20%)
        DiscountChangesDto discountChangesDto = new DiscountChangesDto(productId, 20.0, 10.0);

        // 3. Send message to 'discountChangesTopic'
        kafkaTemplate.send("discountChangesTopic", discountChangesDto);

        // 4. Verify that KafkaSender was called to notify subscribers
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ArgumentCaptor<DiscountNotification> captor = ArgumentCaptor.forClass(DiscountNotification.class);
            verify(kafkaSender, atLeastOnce()).sendDiscountNotification(eq("discountNotifTopic"), captor.capture());
            
            DiscountNotification notification = captor.getValue();
            assertEquals(productId, notification.productId());
            assertEquals(999L, notification.clientId());
            assertEquals(20.0, notification.discount());
        });
    }
}
