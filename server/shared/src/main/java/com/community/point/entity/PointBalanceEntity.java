package com.community.point.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 회원 잔액 — V51__points.sql 의 {@code point_balances}. <b>읽기 전용 매핑</b>이다(바꾸는 일은 {@code PointService} 가 행을 잠그고 JDBC 로 한다). 행이 없으면 0P 다 */
@Entity
@Table(name = "point_balances")
public class PointBalanceEntity {

    @Id
    private Long userId;

    private long balance;
    private Instant updatedAt;

    protected PointBalanceEntity() {
    }

    public Long getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
