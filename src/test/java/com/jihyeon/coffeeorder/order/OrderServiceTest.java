package com.jihyeon.coffeeorder.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jihyeon.coffeeorder.global.exception.BusinessException;
import com.jihyeon.coffeeorder.global.exception.ErrorCode;
import com.jihyeon.coffeeorder.member.entity.Member;
import com.jihyeon.coffeeorder.member.repository.MemberRepository;
import com.jihyeon.coffeeorder.member.repository.PointHistoryRepository;
import com.jihyeon.coffeeorder.member.service.PointService;
import com.jihyeon.coffeeorder.menu.entity.Menu;
import com.jihyeon.coffeeorder.menu.repository.MenuRepository;
import com.jihyeon.coffeeorder.order.dto.OrderCreateRequest;
import com.jihyeon.coffeeorder.order.dto.OrderListResponse;
import com.jihyeon.coffeeorder.order.dto.OrderResponse;
import com.jihyeon.coffeeorder.order.event.OrderCompletedEvent;
import com.jihyeon.coffeeorder.order.repository.OrderRepository;
import com.jihyeon.coffeeorder.order.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@ActiveProfiles("test")
@RecordApplicationEvents
@Import(OrderServiceTest.EventFailureTestConfig.class)
@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PointService pointService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private Member member;
    private Menu americano;
    private Menu latte;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        menuRepository.deleteAll();
        pointHistoryRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(new Member("Jihyeon"));
        pointService.charge(member.getId(), 30000);
        americano = menuRepository.save(new Menu("Americano", 4500));
        latte = menuRepository.save(new Menu("Cafe Latte", 5000));
    }

    @Test
    void createOrderAndPublishCompletedEvent() {
        OrderResponse response = orderService.create(request(
                new OrderCreateRequest.Item(americano.getId(), 2),
                new OrderCreateRequest.Item(latte.getId(), 1)
        ));

        assertThat(response.totalAmount()).isEqualTo(14000);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().lineAmount()).isEqualTo(9000);
        assertThat(pointService.getBalance(member.getId()).pointBalance()).isEqualTo(16000);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(applicationEvents.stream(OrderCompletedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.orderId()).isEqualTo(response.orderId());
                    assertThat(event.memberId()).isEqualTo(member.getId());
                    assertThat(event.totalAmount()).isEqualTo(14000);
                });
    }

    @Test
    void rollbackPointAndOrderWhenPaymentFails() {
        Member poorMember = memberRepository.save(new Member("Poor Member"));
        pointService.charge(poorMember.getId(), 1000);

        assertThatThrownBy(() -> orderService.create(new OrderCreateRequest(
                poorMember.getId(),
                List.of(new OrderCreateRequest.Item(americano.getId(), 1))
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_NOT_ENOUGH);

        assertThat(pointService.getBalance(poorMember.getId()).pointBalance()).isEqualTo(1000);
        assertThat(orderRepository.count()).isZero();
        assertThat(applicationEvents.stream(OrderCompletedEvent.class)).isEmpty();
        assertThat(pointHistoryRepository.findAllByMemberIdOrderByCreatedAtAsc(poorMember.getId())).hasSize(1);
    }

    @Test
    void rollbackPointAndOrderWhenCompletionHandlingFails() {
        assertThatThrownBy(() -> orderService.create(request(
                new OrderCreateRequest.Item(americano.getId(), 3)
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("주문 완료 처리 실패");

        assertThat(pointService.getBalance(member.getId()).pointBalance()).isEqualTo(30000);
        assertThat(orderRepository.count()).isZero();
        assertThat(pointHistoryRepository.findAllByMemberIdOrderByCreatedAtAsc(member.getId())).hasSize(1);
    }

    @Test
    void findOrderAndMemberOrders() {
        OrderResponse first = orderService.create(request(new OrderCreateRequest.Item(americano.getId(), 1)));
        OrderResponse second = orderService.create(request(new OrderCreateRequest.Item(latte.getId(), 1)));

        OrderResponse found = orderService.findById(first.orderId());
        OrderListResponse list = orderService.findAllByMemberId(member.getId());

        assertThat(found.items()).singleElement().satisfies(item -> {
            assertThat(item.menuName()).isEqualTo("Americano");
            assertThat(item.unitPrice()).isEqualTo(4500);
        });
        assertThat(list.orders()).extracting(OrderResponse::orderId)
                .containsExactly(second.orderId(), first.orderId());
    }

    @Test
    void rejectDuplicatedMenu() {
        assertThatThrownBy(() -> orderService.create(request(
                new OrderCreateRequest.Item(americano.getId(), 1),
                new OrderCreateRequest.Item(americano.getId(), 1)
        )))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_ITEM_DUPLICATED);

        assertThat(pointService.getBalance(member.getId()).pointBalance()).isEqualTo(30000);
        assertThat(orderRepository.count()).isZero();
    }

    private OrderCreateRequest request(OrderCreateRequest.Item... items) {
        return new OrderCreateRequest(member.getId(), List.of(items));
    }

    @TestConfiguration
    static class EventFailureTestConfig {

        @Bean
        FailingOrderCompletedListener failingOrderCompletedListener() {
            return new FailingOrderCompletedListener();
        }
    }

    static class FailingOrderCompletedListener {

        @EventListener
        void handle(OrderCompletedEvent event) {
            if (event.totalAmount() == 13500) {
                throw new IllegalStateException("주문 완료 처리 실패");
            }
        }
    }
}
