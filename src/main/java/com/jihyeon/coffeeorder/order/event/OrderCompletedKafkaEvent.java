package com.jihyeon.coffeeorder.order.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCompletedKafkaEvent(
        UUID eventId,
        Long orderId,
        Long memberId,
        long totalAmount,
        Instant completedAt
) {

    public static OrderCompletedKafkaEvent from(OrderCompletedEvent event) {
        return new OrderCompletedKafkaEvent(
                UUID.randomUUID(), event.orderId(), event.memberId(), event.totalAmount(), Instant.now()
        );
    }
}
