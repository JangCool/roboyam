package com.community.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원 잔액 — {@code point_balances}(V51, V85 에서 무상·유상·미회수로 나눔). <b>읽기 전용 매핑</b>이다(바꾸는 일은 {@code PointService} 가 행을 잠그고 JDBC 로 한다). 행이 없으면 0P 다.
 * {@code balance} 는 DB 가 계산하는 합계 칸({@code free_balance + paid_balance})이라 쓰지 못한다.
 */
@Entity
@Table(name = "point_balances")
public class PointBalanceEntity {

    @Id
    private Long userId;

    @Column(insertable = false, updatable = false)
    private long balance;
    private long freeBalance;
    private long paidBalance;
    /** 미회수 포인트 — 다음 무상 적립에서 먼저 갚는다(commerce.md 5-4) */
    private long revokeDebt;
    private Instant updatedAt;

    protected PointBalanceEntity() {
    }

    public Long getUserId() {
        return userId;
    }

    /** 합계(무상 + 유상) */
    public long getBalance() {
        return balance;
    }

    public long getFreeBalance() {
        return freeBalance;
    }

    public long getPaidBalance() {
        return paidBalance;
    }

    public long getRevokeDebt() {
        return revokeDebt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
