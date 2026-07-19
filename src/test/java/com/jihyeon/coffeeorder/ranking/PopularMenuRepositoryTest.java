package com.jihyeon.coffeeorder.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.jihyeon.coffeeorder.menu.entity.Menu;
import com.jihyeon.coffeeorder.menu.repository.MenuRepository;
import com.jihyeon.coffeeorder.order.entity.Order;
import com.jihyeon.coffeeorder.order.entity.OrderStatus;
import com.jihyeon.coffeeorder.order.repository.OrderRepository;
import com.jihyeon.coffeeorder.ranking.repository.PopularMenuProjection;
import com.jihyeon.coffeeorder.ranking.repository.PopularMenuRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
class PopularMenuRepositoryTest {

    @Autowired
    private PopularMenuRepository popularMenuRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MenuRepository menuRepository;

    private Menu americano;
    private Menu latte;
    private Menu tea;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        menuRepository.deleteAll();
        americano = menuRepository.save(new Menu("Americano", 4500));
        latte = menuRepository.save(new Menu("Latte", 5000));
        tea = menuRepository.save(new Menu("Tea", 4000));
    }

    @Test
    void returnEmptyListWhenThereIsNoOrder() {
        assertThat(findPopularMenus()).isEmpty();
    }

    @Test
    void aggregateQuantityAndSortByQuantityOrderCountAndMenuId() {
        saveCompletedOrder(item(americano, 2), item(tea, 1));
        saveCompletedOrder(item(latte, 1));
        saveCompletedOrder(item(latte, 1));
        saveCompletedOrder(item(tea, 1));

        List<PopularMenuProjection> result = findPopularMenus();

        assertThat(result).extracting(PopularMenuProjection::getMenuId)
                .containsExactly(latte.getId(), tea.getId(), americano.getId());
        assertThat(result).extracting(PopularMenuProjection::getTotalQuantity)
                .containsExactly(2L, 2L, 2L);
        assertThat(result).extracting(PopularMenuProjection::getOrderCount)
                .containsExactly(2L, 2L, 1L);
    }

    @Test
    void excludeCanceledOrder() {
        saveCompletedOrder(item(americano, 1));
        Order canceled = new Order(1L);
        canceled.addItem(latte, 100);
        canceled.cancel();
        orderRepository.saveAndFlush(canceled);

        List<PopularMenuProjection> result = findPopularMenus();

        assertThat(result).singleElement().satisfies(menu -> {
            assertThat(menu.getMenuId()).isEqualTo(americano.getId());
            assertThat(menu.getTotalQuantity()).isEqualTo(1);
        });
    }

    private List<PopularMenuProjection> findPopularMenus() {
        return popularMenuRepository.findPopularMenus(OrderStatus.COMPLETED, PageRequest.of(0, 10));
    }

    private void saveCompletedOrder(OrderLine... lines) {
        Order order = new Order(1L);
        for (OrderLine line : lines) {
            order.addItem(line.menu(), line.quantity());
        }
        orderRepository.saveAndFlush(order);
    }

    private OrderLine item(Menu menu, int quantity) {
        return new OrderLine(menu, quantity);
    }

    private record OrderLine(Menu menu, int quantity) {
    }
}
