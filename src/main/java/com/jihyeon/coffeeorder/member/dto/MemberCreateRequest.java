package com.jihyeon.coffeeorder.member.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberCreateRequest(
        @NotBlank String name
) {
}
