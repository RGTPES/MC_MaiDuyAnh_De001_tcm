package org.example.orderservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.orderservice.exceptions.GlobalExceptionHandler;
import org.example.orderservice.exceptions.ProductNotFoundException;
import org.example.orderservice.exceptions.ProductServiceException;
import org.example.orderservice.models.constants.OrderStatus;
import org.example.orderservice.models.dto.requests.CreateOrderDetailRequest;
import org.example.orderservice.models.dto.requests.CreateOrderRequest;
import org.example.orderservice.models.dto.responses.OrderDetailResponse;
import org.example.orderservice.models.dto.responses.OrderResponse;
import org.example.orderservice.models.services.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("POST /api/v1/orders should return 201 Created when request is valid")
    void createOrder_Success_Returns201() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "Nguyen Van A",
                "customer@example.com",
                List.of(new CreateOrderDetailRequest(1L, 2))
        );

        OrderResponse response = new OrderResponse(
                1L,
                "Nguyen Van A",
                2000.0,
                OrderStatus.PENDING,
                List.of(new OrderDetailResponse(10L, 1L, "Laptop", 2, 1000.0, 2000.0))
        );

        when(orderService.createOrder(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.customerName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.total").value(2000.0))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].productId").value(1L))
                .andExpect(jsonPath("$.items[0].productName").value("Laptop"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(2000.0));
    }

    @Test
    @DisplayName("POST /api/v1/orders should return 400 Bad Request when request has invalid data")
    void createOrder_InvalidRequest_Returns400() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "",
                "invalid-email",
                Collections.emptyList()
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    @DisplayName("POST /api/v1/orders should return 404 Not Found when product does not exist")
    void createOrder_ProductNotFound_Returns404() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "Nguyen Van A",
                "customer@example.com",
                List.of(new CreateOrderDetailRequest(999L, 1))
        );

        when(orderService.createOrder(any())).thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    @DisplayName("POST /api/v1/orders should return 502 Bad Gateway when product service is unavailable")
    void createOrder_ProductServiceError_Returns502() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "Nguyen Van A",
                "customer@example.com",
                List.of(new CreateOrderDetailRequest(1L, 1))
        );

        when(orderService.createOrder(any())).thenThrow(new ProductServiceException("Product service is down"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Product service error"));
    }
}
