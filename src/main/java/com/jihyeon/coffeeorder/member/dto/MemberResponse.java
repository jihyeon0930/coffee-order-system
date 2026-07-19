package com.jihyeon.coffeeorder.member.dto;

import com.jihyeon.coffeeorder.member.entity.Member;

public record MemberResponse(
        Long memberId,
        String name,
        long pointBalance
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getName(), member.getPointBalance());
    }
}
