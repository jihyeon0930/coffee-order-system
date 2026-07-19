package com.jihyeon.coffeeorder.order.dto;

import com.jihyeon.coffeeorder.order.entity.OrderItem;

public record OrderItemResponse(
        Long menuId,
        String menuName,
        long unitPrice,
        int quantity,
        long lineAmount
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getMenuId(),
                item.getMenuName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineAmount()
        );
    }
}
