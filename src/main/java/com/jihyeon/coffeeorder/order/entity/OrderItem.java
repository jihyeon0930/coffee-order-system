package com.jihyeon.coffeeorder.order.entity;

import com.jihyeon.coffeeorder.menu.entity.Menu;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long menuId;

    @Column(nullable = false, length = 100)
    private String menuName;

    @Column(nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long lineAmount;

    protected OrderItem() {
    }

    static OrderItem from(Menu menu, int quantity) {
        OrderItem orderItem = new OrderItem();
        orderItem.menuId = menu.getId();
        orderItem.menuName = menu.getName();
        orderItem.unitPrice = menu.getPrice();
        orderItem.quantity = quantity;
        orderItem.lineAmount = Math.multiplyExact(menu.getPrice(), quantity);
        return orderItem;
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public Long getMenuId() {
        return menuId;
    }

    public String getMenuName() {
        return menuName;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getLineAmount() {
        return lineAmount;
    }
}
