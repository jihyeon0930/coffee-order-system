package com.jihyeon.coffeeorder.order.service;

import com.jihyeon.coffeeorder.global.exception.BusinessException;
import com.jihyeon.coffeeorder.global.exception.ErrorCode;
import com.jihyeon.coffeeorder.member.service.PointService;
import com.jihyeon.coffeeorder.menu.entity.Menu;
import com.jihyeon.coffeeorder.menu.entity.MenuStatus;
import com.jihyeon.coffeeorder.menu.repository.MenuRepository;
import com.jihyeon.coffeeorder.order.dto.OrderCreateRequest;
import com.jihyeon.coffeeorder.order.dto.OrderListResponse;
import com.jihyeon.coffeeorder.order.dto.OrderResponse;
import com.jihyeon.coffeeorder.order.entity.Order;
import com.jihyeon.coffeeorder.order.event.OrderCompletedEvent;
import com.jihyeon.coffeeorder.order.repository.OrderRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final PointService pointService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            MenuRepository menuRepository,
            PointService pointService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.menuRepository = menuRepository;
        this.pointService = pointService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse create(OrderCreateRequest request) {
        validateNoDuplicatedMenu(request.items());
        Map<Long, Menu> menus = findMenus(request.items());
        Order order = new Order(request.memberId());

        for (OrderCreateRequest.Item item : request.items()) {
            Menu menu = menus.get(item.menuId());
            validateOnSale(menu);
            order.addItem(menu, item.quantity());
        }

        pointService.use(request.memberId(), order.getTotalAmount());
        Order savedOrder = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCompletedEvent(
                savedOrder.getId(), savedOrder.getMemberId(), savedOrder.getTotalAmount()
        ));
        return OrderResponse.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long orderId) {
        return OrderResponse.from(orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public OrderListResponse findAllByMemberId(Long memberId) {
        return new OrderListResponse(orderRepository.findAllByMemberIdOrderByOrderedAtDesc(memberId).stream()
                .map(OrderResponse::from)
                .toList());
    }

    private Map<Long, Menu> findMenus(List<OrderCreateRequest.Item> items) {
        List<Long> menuIds = items.stream().map(OrderCreateRequest.Item::menuId).toList();
        Map<Long, Menu> menus = menuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(Menu::getId, Function.identity()));
        if (menus.size() != menuIds.size()) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
        }
        return menus;
    }

    private void validateNoDuplicatedMenu(List<OrderCreateRequest.Item> items) {
        Set<Long> menuIds = new HashSet<>();
        if (items.stream().map(OrderCreateRequest.Item::menuId).anyMatch(menuId -> !menuIds.add(menuId))) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_DUPLICATED);
        }
    }

    private void validateOnSale(Menu menu) {
        if (menu.getStatus() != MenuStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.MENU_NOT_ON_SALE);
        }
    }
}
