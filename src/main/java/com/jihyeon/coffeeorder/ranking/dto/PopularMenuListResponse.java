package com.jihyeon.coffeeorder.ranking.dto;

import java.util.List;

public record PopularMenuListResponse(List<PopularMenuResponse> menus) {

    public PopularMenuListResponse {
        menus = List.copyOf(menus);
    }
}
