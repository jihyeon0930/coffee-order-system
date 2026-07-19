package com.jihyeon.coffeeorder.ranking.service;

import com.jihyeon.coffeeorder.order.entity.OrderStatus;
import com.jihyeon.coffeeorder.ranking.cache.PopularMenuCache;
import com.jihyeon.coffeeorder.ranking.dto.PopularMenuListResponse;
import com.jihyeon.coffeeorder.ranking.dto.PopularMenuResponse;
import com.jihyeon.coffeeorder.ranking.repository.PopularMenuProjection;
import com.jihyeon.coffeeorder.ranking.repository.PopularMenuRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PopularMenuService {

    private static final int POPULAR_MENU_LIMIT = 10;

    private final PopularMenuRepository popularMenuRepository;
    private final PopularMenuCache popularMenuCache;

    public PopularMenuService(PopularMenuRepository popularMenuRepository, PopularMenuCache popularMenuCache) {
        this.popularMenuRepository = popularMenuRepository;
        this.popularMenuCache = popularMenuCache;
    }

    @Transactional(readOnly = true)
    public PopularMenuListResponse findPopularMenus() {
        return popularMenuCache.get()
                .map(PopularMenuListResponse::new)
                .orElseGet(this::findFromDatabaseAndCache);
    }

    private PopularMenuListResponse findFromDatabaseAndCache() {
        List<PopularMenuProjection> rows = popularMenuRepository.findPopularMenus(
                OrderStatus.COMPLETED,
                PageRequest.of(0, POPULAR_MENU_LIMIT)
        );
        List<PopularMenuResponse> menus = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            PopularMenuProjection row = rows.get(index);
            menus.add(new PopularMenuResponse(
                    index + 1,
                    row.getMenuId(),
                    row.getMenuName(),
                    row.getTotalQuantity(),
                    row.getOrderCount()
            ));
        }
        List<PopularMenuResponse> immutableMenus = List.copyOf(menus);
        popularMenuCache.put(immutableMenus);
        return new PopularMenuListResponse(immutableMenus);
    }
}
