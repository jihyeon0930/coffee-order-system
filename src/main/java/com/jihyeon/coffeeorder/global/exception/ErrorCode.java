package com.jihyeon.coffeeorder.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND", "메뉴를 찾을 수 없습니다."),
    MENU_NOT_ON_SALE(HttpStatus.CONFLICT, "MENU_NOT_ON_SALE", "판매 중인 메뉴만 주문할 수 있습니다."),
    POINT_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "POINT_AMOUNT_INVALID", "충전 금액은 0보다 커야 합니다."),
    POINT_NOT_ENOUGH(HttpStatus.CONFLICT, "POINT_NOT_ENOUGH", "보유 포인트가 부족합니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    ORDER_ITEM_DUPLICATED(HttpStatus.BAD_REQUEST, "ORDER_ITEM_DUPLICATED", "같은 메뉴를 중복해서 주문할 수 없습니다."),
    ORDER_ALREADY_COMPLETED(HttpStatus.CONFLICT, "ORDER_ALREADY_COMPLETED", "이미 완료된 주문입니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다."),
    CONCURRENCY_CONFLICT(HttpStatus.CONFLICT, "CONCURRENCY_CONFLICT", "동시에 처리된 요청으로 인해 다시 시도해야 합니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
