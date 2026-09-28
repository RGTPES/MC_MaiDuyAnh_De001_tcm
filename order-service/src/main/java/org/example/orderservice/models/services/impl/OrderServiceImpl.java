package org.example.orderservice.models.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.models.constants.OrderStatus;
import org.example.orderservice.models.dto.requests.CreateOrderDetailRequest;
import org.example.orderservice.models.dto.requests.CreateOrderRequest;
import org.example.orderservice.models.dto.responses.OrderDetailResponse;
import org.example.orderservice.models.dto.responses.OrderResponse;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.example.orderservice.models.entities.Order;
import org.example.orderservice.models.entities.OrderDetail;
import org.example.orderservice.models.repositories.OrderDetailRepository;
import org.example.orderservice.models.repositories.OrderRepository;
import org.example.orderservice.models.services.OrderService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String ORDER_CREATED_TOPIC = "order-created";

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductGatewayService productGatewayService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(CreateOrderRequest request) {
        List<ItemSnapshot> snapshots = new ArrayList<>();
        double total = 0.0;

        for (CreateOrderDetailRequest item : request.items()) {
            ProductResponse product = productGatewayService.getProductById(item.productId());
            double unitPrice = product.price();
            double subtotal = unitPrice * item.quantity();
            total += subtotal;
            snapshots.add(new ItemSnapshot(item.productId(), product.name(), item.quantity(), unitPrice, subtotal));
        }

        Order order = Order.builder()
                .customerName(request.customerName())
                .total(total)
                .status(OrderStatus.PENDING)
                .build();
        Order savedOrder = orderRepository.save(order);

        List<OrderDetail> orderDetails = new ArrayList<>();
        for (ItemSnapshot snapshot : snapshots) {
            OrderDetail orderDetail = OrderDetail.builder()
                    .order(savedOrder)
                    .productId(snapshot.productId())
                    .quantity(snapshot.quantity())
                    .unitPrice(snapshot.unitPrice())
                    .build();
            orderDetails.add(orderDetail);
        }
        List<OrderDetail> savedDetails = orderDetailRepository.saveAll(orderDetails);

        List<OrderDetailResponse> detailResponses = new ArrayList<>();
        for (int i = 0; i < savedDetails.size(); i++) {
            OrderDetail savedDetail = savedDetails.get(i);
            ItemSnapshot snapshot = snapshots.get(i);
            detailResponses.add(new OrderDetailResponse(
                    savedDetail.getId(),
                    savedDetail.getProductId(),
                    snapshot.productName(),
                    savedDetail.getQuantity(),
                    savedDetail.getUnitPrice(),
                    snapshot.subtotal()
            ));
        }

        kafkaTemplate.send(ORDER_CREATED_TOPIC, request.customerEmail());
        log.info("Order created successfully with id: {} and event sent to topic {}", savedOrder.getId(), ORDER_CREATED_TOPIC);

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getCustomerName(),
                savedOrder.getTotal(),
                savedOrder.getStatus(),
                detailResponses
        );
    }

    private record ItemSnapshot(
            Long productId,
            String productName,
            Integer quantity,
            Double unitPrice,
            Double subtotal
    ) {
    }
}
