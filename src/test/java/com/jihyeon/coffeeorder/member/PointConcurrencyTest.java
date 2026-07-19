package com.jihyeon.coffeeorder.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.jihyeon.coffeeorder.global.exception.BusinessException;
import com.jihyeon.coffeeorder.global.exception.ErrorCode;
import com.jihyeon.coffeeorder.member.entity.Member;
import com.jihyeon.coffeeorder.member.repository.MemberRepository;
import com.jihyeon.coffeeorder.member.service.PointService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class PointConcurrencyTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void concurrentPointUseDoesNotMakeBalanceNegativeOrLoseUpdates() throws Exception {
        Member member = memberRepository.save(new Member("Jihyeon"));
        pointService.charge(member.getId(), 1000);

        int requestCount = 20;
        int useAmount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger insufficientCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        pointService.use(member.getId(), useAmount);
                        successCount.incrementAndGet();
                    } catch (BusinessException exception) {
                        if (exception.getErrorCode() == ErrorCode.POINT_NOT_ENOUGH) {
                            insufficientCount.incrementAndGet();
                        } else {
                            throw exception;
                        }
                    }
                    return null;
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        long finalBalance = pointService.getBalance(member.getId()).pointBalance();
        assertThat(successCount).hasValue(10);
        assertThat(insufficientCount).hasValue(10);
        assertThat(finalBalance).isZero();
        assertThat(finalBalance).isGreaterThanOrEqualTo(0);
        assertThat(successCount.get() * useAmount + finalBalance).isEqualTo(1000);
    }
}
