package org.example.orderservice.models.services.impl;

import org.example.orderservice.exceptions.ProductNotFoundException;
import org.example.orderservice.exceptions.ProductServiceException;
import org.example.orderservice.models.constants.OrderStatus;
import org.example.orderservice.models.dto.requests.CreateOrderDetailRequest;
import org.example.orderservice.models.dto.requests.CreateOrderRequest;
import org.example.orderservice.models.dto.responses.OrderResponse;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.example.orderservice.models.entities.Order;
import org.example.orderservice.models.entities.OrderDetail;
import org.example.orderservice.models.repositories.OrderDetailRepository;
import org.example.orderservice.models.repositories.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDetailRepository orderDetailRepository;

    @Mock
    private ProductGatewayService productGatewayService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    private CreateOrderRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateOrderRequest(
                "Nguyen Van A",
                "customer@example.com",
                List.of(
                        new CreateOrderDetailRequest(1L, 2),
                        new CreateOrderDetailRequest(2L, 1)
                )
        );
    }

    @Test
    @DisplayName("createOrder should succeed when all products are valid")
    void createOrder_Success() {
        when(productGatewayService.getProductById(1L))
                .thenReturn(new ProductResponse(1L, "Laptop", 1000.0));
        when(productGatewayService.getProductById(2L))
                .thenReturn(new ProductResponse(2L, "Mouse", 50.0));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            return Order.builder()
                    .id(100L)
                    .customerName(order.getCustomerName())
                    .total(order.getTotal())
                    .status(order.getStatus())
                    .build();
        });

        when(orderDetailRepository.saveAll(any())).thenAnswer(invocation -> {
            List<OrderDetail> details = invocation.getArgument(0);
            long idCounter = 1L;
            List<OrderDetail> saved = new java.util.ArrayList<>();
            for (OrderDetail d : details) {
                saved.add(OrderDetail.builder()
                        .id(idCounter++)
                        .order(d.getOrder())
                        .productId(d.getProductId())
                        .quantity(d.getQuantity())
                        .unitPrice(d.getUnitPrice())
                        .build());
            }
            return saved;
        });

        OrderResponse response = orderService.createOrder(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.customerName()).isEqualTo("Nguyen Van A");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        // Total = 1000.0 * 2 + 50.0 * 1 = 2050.0
        assertThat(response.total()).isEqualTo(2050.0);
        assertThat(response.items()).hasSize(2);

        // Verify items
        assertThat(response.items().get(0).productId()).isEqualTo(1L);
        assertThat(response.items().get(0).productName()).isEqualTo("Laptop");
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.items().get(0).unitPrice()).isEqualTo(1000.0);
        assertThat(response.items().get(0).subtotal()).isEqualTo(2000.0);

        assertThat(response.items().get(1).productId()).isEqualTo(2L);
        assertThat(response.items().get(1).productName()).isEqualTo("Mouse");
        assertThat(response.items().get(1).quantity()).isEqualTo(1);
        assertThat(response.items().get(1).unitPrice()).isEqualTo(50.0);
        assertThat(response.items().get(1).subtotal()).isEqualTo(50.0);

        // Verify Order entity saved with PENDING status
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getCustomerName()).isEqualTo("Nguyen Van A");
        assertThat(orderCaptor.getValue().getTotal()).isEqualTo(2050.0);
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);

        // Verify Kafka event published with customerEmail to 'order-created'
        verify(kafkaTemplate).send(eq("order-created"), eq("customer@example.com"));
    }

    @Test
    @DisplayName("createOrder should throw ProductNotFoundException when product does not exist and not save order")
    void createOrder_ProductNotFound_ThrowsException() {
        when(productGatewayService.getProductById(1L))
                .thenThrow(new ProductNotFoundException(1L));

        assertThatThrownBy(() -> orderService.createOrder(validRequest))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product not found with id: 1");

        verify(orderRepository, never()).save(any());
        verify(orderDetailRepository, never()).saveAll(any());
        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    @DisplayName("createOrder should throw ProductServiceException when product service fails and not save order")
    void createOrder_ProductServiceError_ThrowsException() {
        when(productGatewayService.getProductById(1L))
                .thenThrow(new ProductServiceException("Product service is unavailable"));

        assertThatThrownBy(() -> orderService.createOrder(validRequest))
                .isInstanceOf(ProductServiceException.class)
                .hasMessageContaining("Product service is unavailable");

        verify(orderRepository, never()).save(any());
        verify(orderDetailRepository, never()).saveAll(any());
        verify(kafkaTemplate, never()).send(any(), any());
    }
}
