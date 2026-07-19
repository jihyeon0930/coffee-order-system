package com.jihyeon.coffeeorder.ranking.repository;

public interface PopularMenuProjection {

    Long getMenuId();

    String getMenuName();

    long getTotalQuantity();

    long getOrderCount();
}
