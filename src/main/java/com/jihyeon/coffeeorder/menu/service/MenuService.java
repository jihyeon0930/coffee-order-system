package com.jihyeon.coffeeorder.menu.service;

import com.jihyeon.coffeeorder.menu.dto.MenuCreateRequest;
import com.jihyeon.coffeeorder.menu.dto.MenuListResponse;
import com.jihyeon.coffeeorder.menu.dto.MenuResponse;
import com.jihyeon.coffeeorder.menu.entity.Menu;
import com.jihyeon.coffeeorder.menu.entity.MenuStatus;
import com.jihyeon.coffeeorder.menu.repository.MenuRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    @Transactional
    public MenuResponse create(MenuCreateRequest request) {
        Menu menu = menuRepository.save(new Menu(request.name(), request.price()));
        return MenuResponse.from(menu);
    }

    @Transactional(readOnly = true)
    public MenuListResponse findOnSaleMenus() {
        List<MenuResponse> menus = menuRepository.findAllByStatusOrderByIdAsc(MenuStatus.ON_SALE)
                .stream()
                .map(MenuResponse::from)
                .toList();
        return new MenuListResponse(menus);
    }
}
