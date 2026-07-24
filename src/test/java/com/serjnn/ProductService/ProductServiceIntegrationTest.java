package com.serjnn.ProductService;

import com.serjnn.ProductService.dto.ProductRequest;
import com.serjnn.ProductService.dto.ProductResponse;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.repository.ProductRepository;
import com.serjnn.ProductService.repository.SubscribersRepository;
import com.serjnn.ProductService.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Disabled by default because running Testcontainers requires a local Docker daemon (e.g. Docker Desktop) to be active.")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.sql.init.mode=always"
})
public class ProductServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SubscribersRepository subscribersRepository;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    public void setUp() {
        productRepository.deleteAll().block();
        subscribersRepository.deleteAll().block();
    }

    @Test
    public void testCreateAndRetrieveProduct() {
        ProductRequest request = new ProductRequest("iPhone 15", "Apple smartphone", BigDecimal.valueOf(999.99), Category.ELECTRONICS);

        ProductResponse savedProduct = productService.add(request).block();
        assertThat(savedProduct).isNotNull();
        assertThat(savedProduct.id()).isNotNull();
        assertThat(savedProduct.name()).isEqualTo("iPhone 15");
        assertThat(savedProduct.price()).isEqualByComparingTo("999.99");

        webTestClient.get()
                .uri("/api/v1/products/" + savedProduct.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(response -> {
                    assertThat(response.id()).isEqualTo(savedProduct.id());
                    assertThat(response.name()).isEqualTo("iPhone 15");
                    assertThat(response.category()).isEqualTo(Category.ELECTRONICS);
                });
    }

    @Test
    public void testClientSubscription() {
        ProductRequest request = new ProductRequest("Lego Star Wars", "Building block set", BigDecimal.valueOf(49.99), Category.TOYS);
        ProductResponse savedProduct = productService.add(request).block();
        assertThat(savedProduct).isNotNull();

        webTestClient.post()
                .uri("/api/v1/products/" + savedProduct.id() + "/subscriptions/999")
                .exchange()
                .expectStatus().isCreated();

        List<Long> clientIds = subscribersRepository.findClientIdsByProductId(savedProduct.id())
                .collectList()
                .block();
        assertThat(clientIds).containsExactly(999L);
    }
}
