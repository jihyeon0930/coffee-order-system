package com.jihyeon.coffeeorder.order.repository;

import com.jihyeon.coffeeorder.order.entity.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long orderId);

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByMemberIdOrderByOrderedAtDesc(Long memberId);
}
