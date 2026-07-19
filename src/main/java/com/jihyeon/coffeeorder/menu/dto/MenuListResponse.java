package com.jihyeon.coffeeorder.menu.dto;

import java.util.List;

public record MenuListResponse(
        List<MenuResponse> menus
) {
}
