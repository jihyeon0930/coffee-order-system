package com.jihyeon.coffeeorder.ranking.dto;

public record PopularMenuResponse(
        int rank,
        Long menuId,
        String menuName,
        long totalQuantity,
        long orderCount
) {
}
