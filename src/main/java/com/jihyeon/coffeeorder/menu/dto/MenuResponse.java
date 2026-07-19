package com.jihyeon.coffeeorder.menu.dto;

import com.jihyeon.coffeeorder.menu.entity.Menu;

public record MenuResponse(
        Long menuId,
        String name,
        long price
) {

    public static MenuResponse from(Menu menu) {
        return new MenuResponse(menu.getId(), menu.getName(), menu.getPrice());
    }
}
