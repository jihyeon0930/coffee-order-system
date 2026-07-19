package com.jihyeon.coffeeorder.member.dto;

import jakarta.validation.constraints.Positive;

public record PointChargeRequest(
        @Positive long amount
) {
}
