package com.jihyeon.coffeeorder.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderCompletedEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletedEventProducer.class);

    private final KafkaTemplate<String, OrderCompletedKafkaEvent> kafkaTemplate;
    private final String topicName;

    public OrderCompletedEventProducer(
            KafkaTemplate<String, OrderCompletedKafkaEvent> kafkaTemplate,
            @Value("${kafka.topic.order-completed}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publish(OrderCompletedKafkaEvent event) {
        try {
            kafkaTemplate.send(topicName, event.orderId().toString(), event)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error("주문 완료 Kafka 이벤트 발행 실패. eventId={}, orderId={}",
                                    event.eventId(), event.orderId(), exception);
                            return;
                        }
                        log.info("주문 완료 Kafka 이벤트 발행 성공. eventId={}, orderId={}, partition={}, offset={}",
                                event.eventId(), event.orderId(),
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    });
        } catch (RuntimeException exception) {
            log.error("주문 완료 Kafka 이벤트 발행 요청 실패. eventId={}, orderId={}",
                    event.eventId(), event.orderId(), exception);
        }
    }
}
