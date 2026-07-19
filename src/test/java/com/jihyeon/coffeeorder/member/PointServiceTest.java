package com.jihyeon.coffeeorder.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jihyeon.coffeeorder.global.exception.BusinessException;
import com.jihyeon.coffeeorder.global.exception.ErrorCode;
import com.jihyeon.coffeeorder.member.dto.PointResponse;
import com.jihyeon.coffeeorder.member.entity.Member;
import com.jihyeon.coffeeorder.member.repository.MemberRepository;
import com.jihyeon.coffeeorder.member.repository.PointHistoryRepository;
import com.jihyeon.coffeeorder.member.entity.PointHistoryType;
import com.jihyeon.coffeeorder.member.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class PointServiceTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        pointHistoryRepository.deleteAll();
        memberRepository.deleteAll();
        member = memberRepository.save(new Member("Jihyeon"));
    }

    @Test
    void charge() {
        PointResponse response = pointService.charge(member.getId(), 10000);

        assertThat(response.pointBalance()).isEqualTo(10000);
        assertThat(pointHistoryRepository.findAllByMemberIdOrderByCreatedAtAsc(member.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getType()).isEqualTo(PointHistoryType.EARN);
                    assertThat(history.getChangeAmount()).isEqualTo(10000);
                    assertThat(history.getBalanceAfter()).isEqualTo(10000);
                    assertThat(history.getDescription()).isEqualTo("포인트 충전");
                });
    }

    @Test
    void usePoint() {
        pointService.charge(member.getId(), 10000);

        PointResponse response = pointService.use(member.getId(), 4500);

        assertThat(response.pointBalance()).isEqualTo(5500);
        assertThat(pointHistoryRepository.findAllByMemberIdOrderByCreatedAtAsc(member.getId()))
                .hasSize(2)
                .last()
                .satisfies(history -> {
                    assertThat(history.getType()).isEqualTo(PointHistoryType.USE);
                    assertThat(history.getChangeAmount()).isEqualTo(-4500);
                    assertThat(history.getBalanceAfter()).isEqualTo(5500);
                });
    }

    @Test
    void usePointFailsWhenBalanceIsNotEnough() {
        pointService.charge(member.getId(), 1000);

        assertThatThrownBy(() -> pointService.use(member.getId(), 1500))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_NOT_ENOUGH);
        assertThat(pointService.getBalance(member.getId()).pointBalance()).isEqualTo(1000);
        assertThat(pointHistoryRepository.findAllByMemberIdOrderByCreatedAtAsc(member.getId()))
                .singleElement()
                .satisfies(history -> assertThat(history.getType()).isEqualTo(PointHistoryType.EARN));
    }

    @Test
    void pointAmountMustBePositive() {
        assertThatThrownBy(() -> pointService.charge(member.getId(), 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_AMOUNT_INVALID);
        assertThatThrownBy(() -> pointService.use(member.getId(), -1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_AMOUNT_INVALID);
    }
}
