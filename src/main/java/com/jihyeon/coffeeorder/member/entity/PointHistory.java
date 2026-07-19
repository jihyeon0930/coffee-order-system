package com.jihyeon.coffeeorder.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_histories", indexes = {
        @Index(name = "idx_point_histories_member_created_at", columnList = "member_id, created_at")
})
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private long changeAmount;

    @Column(nullable = false)
    private long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointHistoryType type;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PointHistory() {
    }

    private PointHistory(
            Member member,
            long changeAmount,
            long balanceAfter,
            PointHistoryType type,
            String description
    ) {
        this.member = member;
        this.changeAmount = changeAmount;
        this.balanceAfter = balanceAfter;
        this.type = type;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public static PointHistory earn(Member member, long amount, String description) {
        return new PointHistory(member, amount, member.getPointBalance(), PointHistoryType.EARN, description);
    }

    public static PointHistory use(Member member, long amount, String description) {
        return new PointHistory(member, -amount, member.getPointBalance(), PointHistoryType.USE, description);
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public long getChangeAmount() {
        return changeAmount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public PointHistoryType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
