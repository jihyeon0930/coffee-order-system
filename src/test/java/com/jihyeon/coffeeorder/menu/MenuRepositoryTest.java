package com.jihyeon.coffeeorder.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.jihyeon.coffeeorder.menu.entity.Menu;
import com.jihyeon.coffeeorder.menu.entity.MenuStatus;
import com.jihyeon.coffeeorder.menu.repository.MenuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
class MenuRepositoryTest {

    @Autowired
    private MenuRepository menuRepository;

    @Test
    void findAllByStatusOrderByIdAsc() {
        Menu americano = menuRepository.save(new Menu("Americano", 4500));
        Menu latte = menuRepository.save(new Menu("Cafe Latte", 5000));

        assertThat(menuRepository.findAllByStatusOrderByIdAsc(MenuStatus.ON_SALE))
                .extracting(Menu::getId)
                .containsExactly(americano.getId(), latte.getId());
    }
}
