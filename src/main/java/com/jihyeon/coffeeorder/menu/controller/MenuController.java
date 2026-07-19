package com.jihyeon.coffeeorder.menu.controller;

import com.jihyeon.coffeeorder.global.response.ApiResponse;
import com.jihyeon.coffeeorder.menu.dto.MenuCreateRequest;
import com.jihyeon.coffeeorder.menu.dto.MenuListResponse;
import com.jihyeon.coffeeorder.menu.dto.MenuResponse;
import com.jihyeon.coffeeorder.menu.service.MenuService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MenuResponse>> create(@Valid @RequestBody MenuCreateRequest request) {
        MenuResponse response = menuService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/menus/" + response.menuId()))
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<MenuListResponse> findMenus() {
        return ApiResponse.success(menuService.findOnSaleMenus());
    }
}
