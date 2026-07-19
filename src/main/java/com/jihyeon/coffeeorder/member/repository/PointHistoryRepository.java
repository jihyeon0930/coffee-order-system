package com.jihyeon.coffeeorder.member.repository;

import com.jihyeon.coffeeorder.member.entity.PointHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    List<PointHistory> findAllByMemberIdOrderByCreatedAtAsc(Long memberId);
}
