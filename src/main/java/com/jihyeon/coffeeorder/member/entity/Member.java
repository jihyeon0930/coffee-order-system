package com.jihyeon.coffeeorder.member.entity;

import com.jihyeon.coffeeorder.global.exception.BusinessException;
import com.jihyeon.coffeeorder.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long pointBalance;

    protected Member() {
    }

    public Member(String name) {
        this.name = name;
        this.pointBalance = 0L;
    }

    public void charge(long amount) {
        validatePositiveAmount(amount);
        pointBalance = Math.addExact(pointBalance, amount);
    }

    public void use(long amount) {
        validatePositiveAmount(amount);
        if (pointBalance < amount) {
            throw new BusinessException(ErrorCode.POINT_NOT_ENOUGH);
        }
        pointBalance -= amount;
    }

    private void validatePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.POINT_AMOUNT_INVALID);
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPointBalance() {
        return pointBalance;
    }
}
