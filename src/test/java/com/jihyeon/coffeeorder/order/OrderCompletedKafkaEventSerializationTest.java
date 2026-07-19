package com.jihyeon.coffeeorder.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.jihyeon.coffeeorder.order.event.OrderCompletedKafkaEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

class OrderCompletedKafkaEventSerializationTest {

    @Test
    void serializeAndDeserializeEventAsJson() {
        OrderCompletedKafkaEvent event = new OrderCompletedKafkaEvent(
                UUID.randomUUID(), 10L, 1L, 4500L, Instant.parse("2026-07-20T00:00:00Z")
        );
        JsonSerializer<OrderCompletedKafkaEvent> serializer = new JsonSerializer<>();
        JsonDeserializer<OrderCompletedKafkaEvent> deserializer =
                new JsonDeserializer<>(OrderCompletedKafkaEvent.class, false);

        byte[] json = serializer.serialize("coffee.order.completed.v1", event);
        OrderCompletedKafkaEvent restored = deserializer.deserialize("coffee.order.completed.v1", json);

        assertThat(new String(json)).contains("eventId", "orderId", "completedAt");
        assertThat(restored).isEqualTo(event);
    }
}
