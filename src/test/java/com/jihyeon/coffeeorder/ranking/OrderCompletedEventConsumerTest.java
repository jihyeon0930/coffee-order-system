package com.jihyeon.coffeeorder.ranking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jihyeon.coffeeorder.order.event.OrderCompletedKafkaEvent;
import com.jihyeon.coffeeorder.ranking.event.OrderCompletedEventConsumer;
import com.jihyeon.coffeeorder.ranking.event.PopularMenuCacheInvalidator;
import com.jihyeon.coffeeorder.ranking.event.ProcessedOrderEventStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCompletedEventConsumerTest {

    @Mock
    private PopularMenuCacheInvalidator cacheInvalidator;

    @Test
    void ignoreDuplicatedEvent() {
        OrderCompletedEventConsumer consumer = consumer();
        OrderCompletedKafkaEvent event = event();

        consumer.handle(event);
        consumer.handle(event);

        verify(cacheInvalidator, times(1)).invalidate();
    }

    @Test
    void allowRetryWhenEventHandlingFails() {
        OrderCompletedEventConsumer consumer = consumer();
        OrderCompletedKafkaEvent event = event();
        doThrow(new IllegalStateException("Redis 연결 실패"))
                .doNothing()
                .when(cacheInvalidator).invalidate();

        assertThatThrownBy(() -> consumer.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis 연결 실패");
        consumer.handle(event);

        verify(cacheInvalidator, times(2)).invalidate();
    }

    private OrderCompletedEventConsumer consumer() {
        return new OrderCompletedEventConsumer(cacheInvalidator, new ProcessedOrderEventStore());
    }

    private OrderCompletedKafkaEvent event() {
        return new OrderCompletedKafkaEvent(UUID.randomUUID(), 10L, 1L, 4500L, Instant.now());
    }
}
