package com.jihyeon.coffeeorder.ranking.repository;

import com.jihyeon.coffeeorder.order.entity.OrderItem;
import com.jihyeon.coffeeorder.order.entity.OrderStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopularMenuRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            select oi.menuId as menuId,
                   oi.menuName as menuName,
                   sum(oi.quantity) as totalQuantity,
                   count(o.id) as orderCount
            from OrderItem oi
            join oi.order o
            where o.status = :status
            group by oi.menuId, oi.menuName
            order by sum(oi.quantity) desc, count(o.id) desc, oi.menuId asc
            """)
    List<PopularMenuProjection> findPopularMenus(
            @Param("status") OrderStatus status,
            Pageable pageable
    );
}
