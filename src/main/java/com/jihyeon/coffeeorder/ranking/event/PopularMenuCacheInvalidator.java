package com.jihyeon.coffeeorder.ranking.event;

import com.jihyeon.coffeeorder.order.event.OrderCompletedEvent;
import com.jihyeon.coffeeorder.ranking.cache.PopularMenuCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PopularMenuCacheInvalidator {

    private final PopularMenuCache popularMenuCache;

    public PopularMenuCacheInvalidator(PopularMenuCache popularMenuCache) {
        this.popularMenuCache = popularMenuCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCompletedEvent event) {
        popularMenuCache.evict();
    }
}
