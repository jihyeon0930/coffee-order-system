package com.jihyeon.coffeeorder.order.event;

public record OrderCompletedEvent(Long orderId, Long memberId, long totalAmount) {
}
