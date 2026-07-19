package com.jihyeon.coffeeorder.order;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jihyeon.coffeeorder.order.event.OrderCompletedEventProducer;
import com.jihyeon.coffeeorder.order.event.OrderCompletedKafkaEvent;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OrderCompletedEventProducerTest {

    @Test
    void publishEventWithOrderIdAsKey() {
        KafkaTemplate<String, OrderCompletedKafkaEvent> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
        OrderCompletedEventProducer producer = new OrderCompletedEventProducer(kafkaTemplate, "coffee.order.completed.v1");
        OrderCompletedKafkaEvent event = event();
        when(kafkaTemplate.send("coffee.order.completed.v1", "10", event))
                .thenReturn(new CompletableFuture<>());

        producer.publish(event);

        verify(kafkaTemplate).send("coffee.order.completed.v1", "10", event);
    }

    @Test
    void doNotFailOrderFlowWhenKafkaSendFails() {
        KafkaTemplate<String, OrderCompletedKafkaEvent> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
        OrderCompletedEventProducer producer = new OrderCompletedEventProducer(kafkaTemplate, "coffee.order.completed.v1");
        OrderCompletedKafkaEvent event = event();
        when(kafkaTemplate.send("coffee.order.completed.v1", "10", event))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka 연결 실패")));

        assertThatCode(() -> producer.publish(event)).doesNotThrowAnyException();
    }

    private OrderCompletedKafkaEvent event() {
        return new OrderCompletedKafkaEvent(UUID.randomUUID(), 10L, 1L, 4500L, Instant.now());
    }
}
