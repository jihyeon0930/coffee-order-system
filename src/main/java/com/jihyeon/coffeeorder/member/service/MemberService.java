package com.jihyeon.coffeeorder.member.service;

import com.jihyeon.coffeeorder.global.exception.BusinessException;
import com.jihyeon.coffeeorder.global.exception.ErrorCode;
import com.jihyeon.coffeeorder.member.dto.MemberCreateRequest;
import com.jihyeon.coffeeorder.member.dto.MemberResponse;
import com.jihyeon.coffeeorder.member.entity.Member;
import com.jihyeon.coffeeorder.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public MemberResponse create(MemberCreateRequest request) {
        return MemberResponse.from(memberRepository.save(new Member(request.name())));
    }

    @Transactional(readOnly = true)
    public MemberResponse findById(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return MemberResponse.from(member);
    }
}
