package com.jihyeon.coffeeorder.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jihyeon.coffeeorder.member.entity.Member;
import com.jihyeon.coffeeorder.member.repository.MemberRepository;
import com.jihyeon.coffeeorder.member.repository.PointHistoryRepository;
import com.jihyeon.coffeeorder.member.service.PointService;
import com.jihyeon.coffeeorder.menu.entity.Menu;
import com.jihyeon.coffeeorder.menu.repository.MenuRepository;
import com.jihyeon.coffeeorder.order.repository.OrderRepository;
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
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private PointService pointService;

    private Member member;
    private Menu menu;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        menuRepository.deleteAll();
        pointHistoryRepository.deleteAll();
        memberRepository.deleteAll();
        member = memberRepository.save(new Member("Jihyeon"));
        pointService.charge(member.getId(), 10000);
        menu = menuRepository.save(new Menu("Americano", 4500));
    }

    @Test
    void createAndFindOrder() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": %d,
                                  "items": [
                                    {"menuId": %d, "quantity": 2}
                                  ]
                                }
                                """.formatted(member.getId(), menu.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/orders/")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(9000))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();

        Number orderIdValue = com.jayway.jsonpath.JsonPath.read(response, "$.data.orderId");
        long orderId = orderIdValue.longValue();

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.items[0].menuName").value("Americano"));

        mockMvc.perform(get("/api/v1/orders").param("memberId", member.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders.length()").value(1));
    }

    @Test
    void rejectInvalidQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": %d,
                                  "items": [
                                    {"menuId": %d, "quantity": 0}
                                  ]
                                }
                                """.formatted(member.getId(), menu.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
