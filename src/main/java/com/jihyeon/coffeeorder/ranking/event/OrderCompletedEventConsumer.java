package com.jihyeon.coffeeorder.ranking.event;

import com.jihyeon.coffeeorder.order.event.OrderCompletedKafkaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCompletedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletedEventConsumer.class);

    private final PopularMenuCacheInvalidator cacheInvalidator;
    private final ProcessedOrderEventStore processedEventStore;

    public OrderCompletedEventConsumer(
            PopularMenuCacheInvalidator cacheInvalidator,
            ProcessedOrderEventStore processedEventStore
    ) {
        this.cacheInvalidator = cacheInvalidator;
        this.processedEventStore = processedEventStore;
    }

    @KafkaListener(topics = "${kafka.topic.order-completed}", groupId = "${kafka.consumer.order-completed-group}")
    public void handle(OrderCompletedKafkaEvent event) {
        if (!processedEventStore.markIfNew(event.eventId())) {
            log.info("이미 처리한 주문 완료 이벤트를 건너뜁니다. eventId={}, orderId={}",
                    event.eventId(), event.orderId());
            return;
        }

        try {
            cacheInvalidator.invalidate();
            log.info("주문 완료 Kafka 이벤트 처리 성공. eventId={}, orderId={}",
                    event.eventId(), event.orderId());
        } catch (RuntimeException exception) {
            processedEventStore.remove(event.eventId());
            log.error("주문 완료 Kafka 이벤트 처리 실패. eventId={}, orderId={}",
                    event.eventId(), event.orderId(), exception);
            throw exception;
        }
    }
}
