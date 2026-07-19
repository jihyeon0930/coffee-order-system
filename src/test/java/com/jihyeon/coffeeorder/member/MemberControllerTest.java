package com.jihyeon.coffeeorder.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jihyeon.coffeeorder.member.entity.Member;
import com.jihyeon.coffeeorder.member.repository.MemberRepository;
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
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void createMemberWithZeroPoint() throws Exception {
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Jihyeon"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Jihyeon"))
                .andExpect(jsonPath("$.data.pointBalance").value(0));
    }

    @Test
    void chargeAndGetPoint() throws Exception {
        Member member = memberRepository.save(new Member("Jihyeon"));

        mockMvc.perform(post("/api/v1/members/{memberId}/points/charge", member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 10000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointBalance").value(10000));

        mockMvc.perform(get("/api/v1/members/{memberId}/points", member.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointBalance").value(10000));
    }

    @Test
    void chargeFailsWhenAmountIsNotPositive() throws Exception {
        Member member = memberRepository.save(new Member("Jihyeon"));

        mockMvc.perform(post("/api/v1/members/{memberId}/points/charge", member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void getPointFailsWhenMemberDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/members/{memberId}/points", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
