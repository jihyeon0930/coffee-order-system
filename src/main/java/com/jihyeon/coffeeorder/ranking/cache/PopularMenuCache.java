package com.jihyeon.coffeeorder.ranking.cache;

import com.jihyeon.coffeeorder.ranking.dto.PopularMenuResponse;
import java.util.List;
import java.util.Optional;

public interface PopularMenuCache {

    Optional<List<PopularMenuResponse>> get();

    void put(List<PopularMenuResponse> menus);

    void evict();
}
