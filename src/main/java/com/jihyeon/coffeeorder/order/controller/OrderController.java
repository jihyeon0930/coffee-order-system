package com.jihyeon.coffeeorder.order.controller;

import com.jihyeon.coffeeorder.global.response.ApiResponse;
import com.jihyeon.coffeeorder.order.dto.OrderCreateRequest;
import com.jihyeon.coffeeorder.order.dto.OrderListResponse;
import com.jihyeon.coffeeorder.order.dto.OrderResponse;
import com.jihyeon.coffeeorder.order.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody OrderCreateRequest request) {
        OrderResponse response = orderService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + response.orderId()))
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> findById(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.findById(orderId));
    }

    @GetMapping
    public ApiResponse<OrderListResponse> findAllByMemberId(@RequestParam Long memberId) {
        return ApiResponse.success(orderService.findAllByMemberId(memberId));
    }
}
