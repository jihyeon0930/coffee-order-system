package com.jihyeon.coffeeorder.ranking.event;

import com.jihyeon.coffeeorder.ranking.cache.PopularMenuCache;
import org.springframework.stereotype.Component;

@Component
public class PopularMenuCacheInvalidator {

    private final PopularMenuCache popularMenuCache;

    public PopularMenuCacheInvalidator(PopularMenuCache popularMenuCache) {
        this.popularMenuCache = popularMenuCache;
    }

    public void invalidate() {
        popularMenuCache.evict();
    }
}
