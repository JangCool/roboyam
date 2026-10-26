package com.community.point.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 원장 한 줄 — {@code point_ledger}(V51, V85 에서 무상·유상 몫과 종류 CANCEL_USE·EXPIRE·REFUND 추가). <b>읽기 전용 매핑</b>이다: 줄은 {@code PointService} 만 JDBC 로 넣고(잔액 행 잠금과 한 흐름) 고치지 않는다(DB 트리거가 막는다).
 */
@Entity
@Table(name = "point_ledger")
public class PointLedgerEntity {

    @Id
    private Long id;

    private Long userId;
    private String kind;
    private String reason;
    private int delta;
    /** 무상 몫(부호 포함, V85) — {@code freeDelta + paidDelta = delta} */
    private long freeDelta;
    /** 유상 몫(부호 포함, V85) */
    private long paidDelta;
    private long balanceAfter;
    private String note;
    private String refType;
    private Long refId;
    private Long actorOperatorId;
    private Instant createdAt;

    protected PointLedgerEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getKind() {
        return kind;
    }

    public String getReason() {
        return reason;
    }

    public int getDelta() {
        return delta;
    }

    public long getFreeDelta() {
        return freeDelta;
    }

    public long getPaidDelta() {
        return paidDelta;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public String getNote() {
        return note;
    }

    public String getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public Long getActorOperatorId() {
        return actorOperatorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
