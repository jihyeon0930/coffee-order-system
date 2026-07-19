package com.jihyeon.coffeeorder.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record OrderCreateRequest(
        @NotNull Long memberId,
        @NotEmpty List<@Valid Item> items
) {
    public record Item(
            @NotNull Long menuId,
            @Positive int quantity
    ) {
    }
}
