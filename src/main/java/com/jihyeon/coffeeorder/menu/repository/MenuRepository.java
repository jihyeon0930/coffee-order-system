package com.jihyeon.coffeeorder.menu.repository;

import com.jihyeon.coffeeorder.menu.entity.Menu;
import com.jihyeon.coffeeorder.menu.entity.MenuStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findAllByStatusOrderByIdAsc(MenuStatus status);
}
