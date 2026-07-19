package com.jihyeon.coffeeorder.menu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jihyeon.coffeeorder.menu.entity.Menu;
import com.jihyeon.coffeeorder.menu.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuRepository menuRepository;

    @BeforeEach
    void setUp() {
        menuRepository.deleteAll();
    }

    @Test
    void createMenu() throws Exception {
        mockMvc.perform(post("/api/v1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Americano",
                                  "price": 4500
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Americano"))
                .andExpect(jsonPath("$.data.price").value(4500));
    }

    @Test
    void findMenu() throws Exception {
        Menu menu = menuRepository.save(new Menu("Cafe Latte", 5000));

        mockMvc.perform(get("/api/v1/menus/{menuId}", menu.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.menuId").value(menu.getId()))
                .andExpect(jsonPath("$.data.name").value("Cafe Latte"))
                .andExpect(jsonPath("$.data.price").value(5000));
    }

    @Test
    void findMenuFailsWhenMenuDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/menus/{menuId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
    }

    @Test
    void findMenus() throws Exception {
        menuRepository.save(new Menu("Americano", 4500));
        menuRepository.save(new Menu("Cafe Latte", 5000));

        mockMvc.perform(get("/api/v1/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.menus").isArray())
                .andExpect(jsonPath("$.data.menus.length()").value(2));
    }

    @Test
    void findMenusReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.menus").isArray())
                .andExpect(jsonPath("$.data.menus.length()").value(0));
    }

    @Test
    void createMenuFailsWhenPriceIsNotPositive() throws Exception {
        mockMvc.perform(post("/api/v1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Americano",
                                  "price": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
