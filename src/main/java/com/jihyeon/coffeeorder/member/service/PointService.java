package com.jihyeon.coffeeorder.member.service;

import com.jihyeon.coffeeorder.global.exception.BusinessException;
import com.jihyeon.coffeeorder.global.exception.ErrorCode;
import com.jihyeon.coffeeorder.member.dto.PointResponse;
import com.jihyeon.coffeeorder.member.entity.Member;
import com.jihyeon.coffeeorder.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {

    private final MemberRepository memberRepository;

    public PointService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public PointResponse charge(Long memberId, long amount) {
        Member member = findMemberForUpdate(memberId);
        member.charge(amount);
        return PointResponse.from(member);
    }

    @Transactional(readOnly = true)
    public PointResponse getBalance(Long memberId) {
        return PointResponse.from(findMember(memberId));
    }

    @Transactional
    public PointResponse use(Long memberId, long amount) {
        Member member = findMemberForUpdate(memberId);
        member.use(amount);
        return PointResponse.from(member);
    }

    private Member findMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
