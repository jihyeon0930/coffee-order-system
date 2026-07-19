package com.jihyeon.coffeeorder.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.jihyeon.coffeeorder.order.event.OrderCompletedEventProducer;
import com.jihyeon.coffeeorder.order.event.OrderCompletedKafkaEvent;
import com.jihyeon.coffeeorder.ranking.cache.PopularMenuCache;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = "coffee.order.completed.v1")
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "kafka.producer.enabled=true",
        "kafka.consumer.order-completed-group=ranking-order-completed-integration-test"
})
class OrderCompletedKafkaIntegrationTest {

    @Autowired
    private OrderCompletedEventProducer producer;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @MockBean
    private PopularMenuCache popularMenuCache;

    @Test
    void createTopicAndDeliverJsonEventToConsumer() throws Exception {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            assertThat(adminClient.listTopics().names().get(10, TimeUnit.SECONDS))
                    .contains("coffee.order.completed.v1");
        }

        OrderCompletedKafkaEvent event = new OrderCompletedKafkaEvent(
                UUID.randomUUID(), 10L, 1L, 4500L, Instant.now()
        );
        producer.publish(event);

        verify(popularMenuCache, timeout(10_000)).evict();
    }
}
