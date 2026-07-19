package com.jihyeon.coffeeorder.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record MenuCreateRequest(
        @NotBlank String name,
        @Positive long price
) {
}
