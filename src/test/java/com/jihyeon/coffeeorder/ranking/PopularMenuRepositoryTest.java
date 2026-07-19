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
import java.time.LocalDateTime;
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
    private Menu mocha;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 20, 12, 0);

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        menuRepository.deleteAll();
        americano = menuRepository.save(new Menu("Americano", 4500));
        latte = menuRepository.save(new Menu("Latte", 5000));
        tea = menuRepository.save(new Menu("Tea", 4000));
        mocha = menuRepository.save(new Menu("Mocha", 5500));
    }

    @Test
    void returnEmptyListWhenThereIsNoOrder() {
        assertThat(findPopularMenus()).isEmpty();
    }

    @Test
    void aggregateLastSevenDaysAndReturnOnlyTopThree() {
        saveCompletedOrder(now.minusDays(1), item(americano, 4));
        saveCompletedOrder(now.minusDays(2), item(latte, 3));
        saveCompletedOrder(now.minusDays(3), item(tea, 2));
        saveCompletedOrder(now.minusDays(4), item(mocha, 1));
        saveCompletedOrder(now.minusDays(8), item(mocha, 100));

        List<PopularMenuProjection> result = findPopularMenus();

        assertThat(result).extracting(PopularMenuProjection::getMenuId)
                .containsExactly(americano.getId(), latte.getId(), tea.getId());
        assertThat(result).extracting(PopularMenuProjection::getTotalQuantity)
                .containsExactly(4L, 3L, 2L);
        assertThat(result).hasSize(3);
    }

    @Test
    void excludeCanceledOrder() {
        saveCompletedOrder(now.minusHours(1), item(americano, 1));
        Order canceled = new Order(1L);
        canceled.addItem(latte, 100);
        canceled.cancel();
        org.springframework.test.util.ReflectionTestUtils.setField(canceled, "orderedAt", now.minusHours(1));
        orderRepository.saveAndFlush(canceled);

        List<PopularMenuProjection> result = findPopularMenus();

        assertThat(result).singleElement().satisfies(menu -> {
            assertThat(menu.getMenuId()).isEqualTo(americano.getId());
            assertThat(menu.getTotalQuantity()).isEqualTo(1);
        });
    }

    @Test
    void sortTiesByOrderCountThenMenuIdConsistently() {
        saveCompletedOrder(now.minusDays(1), item(americano, 2));
        saveCompletedOrder(now.minusDays(1), item(latte, 1));
        saveCompletedOrder(now.minusDays(2), item(latte, 1));
        saveCompletedOrder(now.minusDays(1), item(tea, 2));

        List<PopularMenuProjection> result = findPopularMenus();

        assertThat(result).extracting(PopularMenuProjection::getMenuId)
                .containsExactly(latte.getId(), americano.getId(), tea.getId());
    }

    private List<PopularMenuProjection> findPopularMenus() {
        return popularMenuRepository.findPopularMenus(
                OrderStatus.COMPLETED,
                now.minusDays(7),
                now,
                PageRequest.of(0, 3)
        );
    }

    private void saveCompletedOrder(LocalDateTime orderedAt, OrderLine... lines) {
        Order order = new Order(1L);
        for (OrderLine line : lines) {
            order.addItem(line.menu(), line.quantity());
        }
        org.springframework.test.util.ReflectionTestUtils.setField(order, "orderedAt", orderedAt);
        orderRepository.saveAndFlush(order);
    }

    private OrderLine item(Menu menu, int quantity) {
        return new OrderLine(menu, quantity);
    }

    private record OrderLine(Menu menu, int quantity) {
    }
}
