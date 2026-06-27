package com.serjnn.ProductService;

import com.serjnn.ProductService.controller.ProductController;
import com.serjnn.ProductService.dto.ProductRequest;
import com.serjnn.ProductService.dto.ProductResponse;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ProductController.class)
public class ProductControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ProductService productService;

    @Test
    public void testGetProducts_All() {
        ProductResponse p1 = new ProductResponse(1L, "Prod1", "Desc1", BigDecimal.valueOf(10.0), Category.ELECTRONICS);
        ProductResponse p2 = new ProductResponse(2L, "Prod2", "Desc2", BigDecimal.valueOf(20.0), Category.FOOD);

        when(productService.findAll()).thenReturn(Flux.just(p1, p2));

        webTestClient.get()
                .uri("/api/v1/products")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProductResponse.class)
                .hasSize(2)
                .contains(p1, p2);
    }

    @Test
    public void testGetProductById_Found() {
        ProductResponse p = new ProductResponse(1L, "Prod1", "Desc1", BigDecimal.valueOf(10.0), Category.ELECTRONICS);

        when(productService.findProductById(1L)).thenReturn(Mono.just(p));

        webTestClient.get()
                .uri("/api/v1/products/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .isEqualTo(p);
    }

    @Test
    public void testGetProductById_NotFound() {
        when(productService.findProductById(1L)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/v1/products/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    public void testAddProduct_Success() {
        ProductRequest req = new ProductRequest("Prod1", "Desc1", BigDecimal.valueOf(10.0), Category.ELECTRONICS);
        ProductResponse res = new ProductResponse(1L, "Prod1", "Desc1", BigDecimal.valueOf(10.0), Category.ELECTRONICS);

        when(productService.add(any(ProductRequest.class))).thenReturn(Mono.just(res));

        webTestClient.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductResponse.class)
                .isEqualTo(res);
    }

    @Test
    public void testSubscribe_Success() {
        when(productService.subscribe(100L, 1L)).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/api/v1/products/1/subscriptions/100")
                .exchange()
                .expectStatus().isCreated();
    }
}
