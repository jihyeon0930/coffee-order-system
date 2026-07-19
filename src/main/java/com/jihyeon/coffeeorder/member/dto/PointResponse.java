package com.jihyeon.coffeeorder.member.dto;

import com.jihyeon.coffeeorder.member.entity.Member;

public record PointResponse(
        Long memberId,
        long pointBalance
) {

    public static PointResponse from(Member member) {
        return new PointResponse(member.getId(), member.getPointBalance());
    }
}
