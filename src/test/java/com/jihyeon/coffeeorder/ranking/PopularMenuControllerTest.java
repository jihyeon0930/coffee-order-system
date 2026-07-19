package com.jihyeon.coffeeorder.ranking;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jihyeon.coffeeorder.ranking.controller.PopularMenuController;
import com.jihyeon.coffeeorder.ranking.dto.PopularMenuListResponse;
import com.jihyeon.coffeeorder.ranking.dto.PopularMenuResponse;
import com.jihyeon.coffeeorder.ranking.service.PopularMenuService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PopularMenuController.class)
class PopularMenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PopularMenuService popularMenuService;

    @Test
    void findPopularMenus() throws Exception {
        when(popularMenuService.findPopularMenus()).thenReturn(new PopularMenuListResponse(List.of(
                new PopularMenuResponse(1, 1L, "Americano", 7, 3)
        )));

        mockMvc.perform(get("/api/v1/menus/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.menus[0].rank").value(1))
                .andExpect(jsonPath("$.data.menus[0].menuName").value("Americano"))
                .andExpect(jsonPath("$.data.menus[0].totalQuantity").value(7))
                .andExpect(jsonPath("$.data.menus[0].orderCount").value(3));
    }
}
