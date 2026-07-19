package com.jihyeon.coffeeorder.ranking.controller;

import com.jihyeon.coffeeorder.global.response.ApiResponse;
import com.jihyeon.coffeeorder.ranking.dto.PopularMenuListResponse;
import com.jihyeon.coffeeorder.ranking.service.PopularMenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menus/popular")
public class PopularMenuController {

    private final PopularMenuService popularMenuService;

    public PopularMenuController(PopularMenuService popularMenuService) {
        this.popularMenuService = popularMenuService;
    }

    @GetMapping
    public ApiResponse<PopularMenuListResponse> findPopularMenus() {
        return ApiResponse.success(popularMenuService.findPopularMenus());
    }
}
