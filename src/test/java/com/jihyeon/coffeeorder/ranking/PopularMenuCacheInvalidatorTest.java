package com.jihyeon.coffeeorder.ranking;

import static org.mockito.Mockito.verify;

import com.jihyeon.coffeeorder.ranking.cache.PopularMenuCache;
import com.jihyeon.coffeeorder.ranking.event.PopularMenuCacheInvalidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopularMenuCacheInvalidatorTest {

    @Mock
    private PopularMenuCache popularMenuCache;

    @Test
    void evictCacheAfterOrderCompletionEvent() {
        PopularMenuCacheInvalidator invalidator = new PopularMenuCacheInvalidator(popularMenuCache);

        invalidator.invalidate();

        verify(popularMenuCache).evict();
    }
}
