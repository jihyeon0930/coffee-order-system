package com.jihyeon.coffeeorder.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jihyeon.coffeeorder.order.entity.OrderStatus;
import com.jihyeon.coffeeorder.ranking.cache.PopularMenuCache;
import com.jihyeon.coffeeorder.ranking.dto.PopularMenuListResponse;
import com.jihyeon.coffeeorder.ranking.dto.PopularMenuResponse;
import com.jihyeon.coffeeorder.ranking.repository.PopularMenuProjection;
import com.jihyeon.coffeeorder.ranking.repository.PopularMenuRepository;
import com.jihyeon.coffeeorder.ranking.service.PopularMenuService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PopularMenuServiceTest {

    @Mock
    private PopularMenuRepository popularMenuRepository;

    @Mock
    private PopularMenuCache popularMenuCache;

    @Mock
    private PopularMenuProjection projection;

    @Test
    void cacheMissQueriesDatabaseAndStoresResult() {
        when(popularMenuCache.get()).thenReturn(Optional.empty());
        when(popularMenuRepository.findPopularMenus(any(OrderStatus.class), any(Pageable.class)))
                .thenReturn(List.of(projection));
        when(projection.getMenuId()).thenReturn(1L);
        when(projection.getMenuName()).thenReturn("Americano");
        when(projection.getTotalQuantity()).thenReturn(7L);
        when(projection.getOrderCount()).thenReturn(3L);

        PopularMenuListResponse response = service().findPopularMenus();

        assertThat(response.menus()).containsExactly(new PopularMenuResponse(1, 1L, "Americano", 7, 3));
        verify(popularMenuRepository).findPopularMenus(any(OrderStatus.class), any(Pageable.class));
        verify(popularMenuCache).put(response.menus());
    }

    @Test
    void cacheHitDoesNotQueryDatabase() {
        List<PopularMenuResponse> cached = List.of(new PopularMenuResponse(1, 1L, "Americano", 7, 3));
        when(popularMenuCache.get()).thenReturn(Optional.of(cached));

        PopularMenuListResponse response = service().findPopularMenus();

        assertThat(response.menus()).isEqualTo(cached);
        verify(popularMenuRepository, never()).findPopularMenus(any(), any());
        verify(popularMenuCache, never()).put(any());
    }

    private PopularMenuService service() {
        return new PopularMenuService(popularMenuRepository, popularMenuCache);
    }
}
