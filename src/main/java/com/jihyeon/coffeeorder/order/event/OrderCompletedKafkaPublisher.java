package com.jihyeon.coffeeorder.order.event;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
public class OrderCompletedKafkaPublisher {

    private final OrderCompletedEventProducer producer;

    public OrderCompletedKafkaPublisher(OrderCompletedEventProducer producer) {
        this.producer = producer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCompletedEvent event) {
        producer.publish(OrderCompletedKafkaEvent.from(event));
    }
}
