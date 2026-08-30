package com.serjnn.ProductService.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serjnn.ProductService.dtos.IdsRequest;
import com.serjnn.ProductService.enums.Category;
import com.serjnn.ProductService.exceptions.DuplicateSubscriptionException;
import com.serjnn.ProductService.exceptions.GlobalExceptionHandler;
import com.serjnn.ProductService.exceptions.ProductNotFoundException;
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
        // Blank name, negative price, null category
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
}
