package com.serjnn.ProductService.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.exceptions.DuplicateSubscriptionException;
import com.serjnn.ProductService.exceptions.GlobalExceptionHandler;
import com.serjnn.ProductService.exceptions.ProductNotFoundException;
import com.serjnn.ProductService.exceptions.SubscriptionNotFoundException;
import com.serjnn.ProductService.models.Product;
import com.serjnn.ProductService.services.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("GET /api/v1/products - Should return 200 and slice of products")
    void shouldReturnAllProducts() throws Exception {
        Product p = new Product(1L, "Laptop", "Good laptop", new BigDecimal("1200.00"), Category.ELECTRONICS);
        when(productService.findAll(any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(p), PageRequest.of(0, 10), false));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Laptop"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Should return 200 when product exists")
    void shouldReturnProductById() throws Exception {
        Long productId = 1L;
        Product p = new Product(productId, "Phone", "Nice phone", new BigDecimal("799.00"), Category.ELECTRONICS);
        when(productService.getByIdWithDiscount(productId)).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Phone"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Should return 404 ProblemDetail when product not found")
    void shouldReturn404WhenProductNotFound() throws Exception {
        Long productId = 999L;
        when(productService.getByIdWithDiscount(productId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/v1/products - Should return 201 when product is valid")
    void shouldCreateProductSuccessfully() throws Exception {
        Product p = new Product(null, "Shoes", "Running shoes", new BigDecimal("89.99"), Category.CLOTH);
        when(productService.add(any(Product.class))).thenReturn(42L);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isCreated())
                .andExpect(content().string("42"));
    }

    @Test
    @DisplayName("POST /api/v1/products - Should return 400 Bad Request when validation fails")
    void shouldReturn400WhenProductInvalid() throws Exception {
        Product invalidProduct = new Product(null, "", "Desc", new BigDecimal("-10.00"), null);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidProduct)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request Parameters"))
                .andExpect(jsonPath("$.invalidParams.name").exists())
                .andExpect(jsonPath("$.invalidParams.price").exists())
                .andExpect(jsonPath("$.invalidParams.category").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/products/{id} - Should update and return 200")
    void shouldUpdateProductSuccessfully() throws Exception {
        Long id = 1L;
        Product updatePayload = new Product(null, "Updated Tablet", "Desc", new BigDecimal("299.99"), Category.ELECTRONICS);
        Product updated = new Product(id, "Updated Tablet", "Desc", new BigDecimal("299.99"), Category.ELECTRONICS);

        when(productService.update(eq(id), any(Product.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated Tablet"));
    }

    @Test
    @DisplayName("PUT /api/v1/products/{id} - Should return 404 when product does not exist")
    void shouldReturn404WhenUpdatingNonExistentProduct() throws Exception {
        Long id = 999L;
        Product updatePayload = new Product(null, "Updated Tablet", "Desc", new BigDecimal("299.99"), Category.ELECTRONICS);

        when(productService.update(eq(id), any(Product.class))).thenThrow(new ProductNotFoundException(id));

        mockMvc.perform(put("/api/v1/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product Not Found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id} - Should return 204 No Content")
    void shouldDeleteProductSuccessfully() throws Exception {
        Long id = 1L;
        doNothing().when(productService).delete(id);

        mockMvc.perform(delete("/api/v1/products/{id}", id))
                .andExpect(status().isNoContent());

        verify(productService).delete(id);
    }

    @Test
    @DisplayName("GET /api/v1/products/search - Should return 200 with search results")
    void shouldSearchProducts() throws Exception {
        Product p = new Product(1L, "Laptop", "Good laptop", new BigDecimal("1200.00"), Category.ELECTRONICS);
        when(productService.search(eq("laptop"), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(p), PageRequest.of(0, 10), false));

        mockMvc.perform(get("/api/v1/products/search").param("keyword", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Laptop"));
    }

    @Test
    @DisplayName("POST /api/v1/products/by-ids - Should return 400 when IDs list is empty")
    void shouldReturn400WhenIdsListEmpty() throws Exception {
        IdsRequest emptyRequest = new IdsRequest(Collections.emptyList());

        mockMvc.perform(post("/api/v1/products/by-ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request Parameters"));
    }

    @Test
    @DisplayName("POST /api/v1/products/{id}/subscribe/{clientId} - Should return 409 Conflict when duplicate")
    void shouldReturn409WhenDuplicateSubscription() throws Exception {
        Long productId = 10L;
        Long clientId = 100L;
        doThrow(new DuplicateSubscriptionException(productId, clientId))
                .when(productService).subscribe(clientId, productId);

        mockMvc.perform(post("/api/v1/products/{productId}/subscribe/{clientId}", productId, clientId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Subscription"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id}/subscribe/{clientId} - Should return 204 No Content on unsubscribe")
    void shouldUnsubscribeSuccessfully() throws Exception {
        Long productId = 10L;
        Long clientId = 100L;
        doNothing().when(productService).unsubscribe(clientId, productId);

        mockMvc.perform(delete("/api/v1/products/{productId}/subscribe/{clientId}", productId, clientId))
                .andExpect(status().isNoContent());

        verify(productService).unsubscribe(clientId, productId);
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id}/subscribe/{clientId} - Should return 404 when subscription not found")
    void shouldReturn404WhenUnsubscribingNonExistent() throws Exception {
        Long productId = 10L;
        Long clientId = 100L;
        doThrow(new SubscriptionNotFoundException(productId, clientId))
                .when(productService).unsubscribe(clientId, productId);

        mockMvc.perform(delete("/api/v1/products/{productId}/subscribe/{clientId}", productId, clientId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Subscription Not Found"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{productId}/subscribers - Should return 200 with subscriber IDs")
    void shouldGetProductSubscribers() throws Exception {
        Long productId = 10L;
        when(productService.getSubscriberClientIds(eq(productId), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(100L, 101L), PageRequest.of(0, 10), false));

        mockMvc.perform(get("/api/v1/products/{productId}/subscribers", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0]").value(100))
                .andExpect(jsonPath("$.content[1]").value(101));
    }

    @Test
    @DisplayName("GET /api/v1/products/client/{clientId}/subscriptions - Should return 200 with product IDs")
    void shouldGetClientSubscriptions() throws Exception {
        Long clientId = 100L;
        when(productService.getSubscribedProductIds(eq(clientId), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(10L, 20L), PageRequest.of(0, 10), false));

        mockMvc.perform(get("/api/v1/products/client/{clientId}/subscriptions", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0]").value(10))
                .andExpect(jsonPath("$.content[1]").value(20));
    }
}
