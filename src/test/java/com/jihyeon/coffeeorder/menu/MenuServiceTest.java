package com.jihyeon.coffeeorder.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jihyeon.coffeeorder.global.exception.BusinessException;
import com.jihyeon.coffeeorder.global.exception.ErrorCode;
import com.jihyeon.coffeeorder.menu.dto.MenuCreateRequest;
import com.jihyeon.coffeeorder.menu.dto.MenuResponse;
import com.jihyeon.coffeeorder.menu.entity.Menu;
import com.jihyeon.coffeeorder.menu.repository.MenuRepository;
import com.jihyeon.coffeeorder.menu.service.MenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class MenuServiceTest {

    @Autowired
    private MenuService menuService;

    @Autowired
    private MenuRepository menuRepository;

    @BeforeEach
    void setUp() {
        menuRepository.deleteAll();
    }

    @Test
    void create() {
        MenuResponse response = menuService.create(new MenuCreateRequest("Americano", 4500));

        assertThat(response.menuId()).isNotNull();
        assertThat(response.name()).isEqualTo("Americano");
        assertThat(response.price()).isEqualTo(4500);
    }

    @Test
    void findById() {
        Menu menu = menuRepository.save(new Menu("Cafe Latte", 5000));

        MenuResponse response = menuService.findById(menu.getId());

        assertThat(response.menuId()).isEqualTo(menu.getId());
        assertThat(response.name()).isEqualTo("Cafe Latte");
        assertThat(response.price()).isEqualTo(5000);
    }

    @Test
    void findByIdFailsWhenMenuDoesNotExist() {
        assertThatThrownBy(() -> menuService.findById(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MENU_NOT_FOUND);
    }

    @Test
    void findOnSaleMenus() {
        menuRepository.save(new Menu("Americano", 4500));
        menuRepository.save(new Menu("Cafe Latte", 5000));

        assertThat(menuService.findOnSaleMenus().menus())
                .hasSize(2)
                .extracting(MenuResponse::name)
                .containsExactly("Americano", "Cafe Latte");
    }
}
