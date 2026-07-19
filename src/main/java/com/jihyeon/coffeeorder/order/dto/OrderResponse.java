package com.jihyeon.coffeeorder.order.dto;

import com.jihyeon.coffeeorder.order.entity.Order;
import com.jihyeon.coffeeorder.order.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        Long memberId,
        OrderStatus status,
        long totalAmount,
        LocalDateTime orderedAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
