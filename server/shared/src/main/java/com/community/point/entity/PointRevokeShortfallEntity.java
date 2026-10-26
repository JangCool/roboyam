package com.community.point.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 미회수 기록 — {@code point_revoke_shortfalls}(V85). 회수할 양({@code requested})보다 무상 잔액이 모자라 덜 회수한({@code applied}) 기록이다. 모자란 양은 잔액 행의
 * {@code revoke_debt} 에 더해져 다음 적립에서 갚는다(commerce.md 5-4). <b>읽기 전용 매핑</b>(쓰기는 {@code PointService})
 */
@Entity
@Table(name = "point_revoke_shortfalls")
public class PointRevokeShortfallEntity {

    @Id
    private Long id;

    private Long userId;
    /** 회수 사유(POST_REVOKE 등) */
    private String reason;
    private String refType;
    private Long refId;
    private long requested;
    private long applied;
    private Instant createdAt;

    protected PointRevokeShortfallEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public String getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public long getRequested() {
        return requested;
    }

    public long getApplied() {
        return applied;
    }

    /** 모자란 양 */
    public long shortAmount() {
        return requested - applied;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
