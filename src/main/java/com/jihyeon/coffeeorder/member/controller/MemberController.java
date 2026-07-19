package com.jihyeon.coffeeorder.member.controller;

import com.jihyeon.coffeeorder.global.response.ApiResponse;
import com.jihyeon.coffeeorder.member.dto.MemberCreateRequest;
import com.jihyeon.coffeeorder.member.dto.MemberResponse;
import com.jihyeon.coffeeorder.member.dto.PointChargeRequest;
import com.jihyeon.coffeeorder.member.dto.PointResponse;
import com.jihyeon.coffeeorder.member.service.MemberService;
import com.jihyeon.coffeeorder.member.service.PointService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;
    private final PointService pointService;

    public MemberController(MemberService memberService, PointService pointService) {
        this.memberService = memberService;
        this.pointService = pointService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MemberResponse>> create(@Valid @RequestBody MemberCreateRequest request) {
        MemberResponse response = memberService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/members/" + response.memberId()))
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{memberId}")
    public ApiResponse<MemberResponse> findMember(@PathVariable Long memberId) {
        return ApiResponse.success(memberService.findById(memberId));
    }

    @PostMapping("/{memberId}/points/charge")
    public ApiResponse<PointResponse> charge(
            @PathVariable Long memberId,
            @Valid @RequestBody PointChargeRequest request
    ) {
        return ApiResponse.success(pointService.charge(memberId, request.amount()));
    }

    @GetMapping("/{memberId}/points")
    public ApiResponse<PointResponse> getBalance(@PathVariable Long memberId) {
        return ApiResponse.success(pointService.getBalance(memberId));
    }
}
