package com.jihyeon.coffeeorder.order.dto;

import java.util.List;

public record OrderListResponse(List<OrderResponse> orders) {
}
