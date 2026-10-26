package com.community.point.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포인트 묶음 — {@code point_lots}(V85). <b>읽기 전용 매핑</b>이다(만들고 남은 양을 바꾸는 일은 {@code PointService} 가 잔액 행을 잠근 뒤 JDBC 로 한다).
 * 무상은 소멸일이 반드시 있고 유상은 없다. {@code restoredFromLotId} 가 있으면 만료된 묶음을 되돌려 새로 만든 묶음이다.
 */
@Entity
@Table(name = "point_lots")
public class PointLotEntity {

    @Id
    private Long id;

    private Long userId;
    @Enumerated(EnumType.STRING)
    private PointLotKind kind;
    private long originalAmount;
    private long remainingAmount;
    private Instant expiresAt;
    /** 원장 사유 코드 또는 {@link PointReason#MIGRATION_LOT_SOURCE} */
    private String sourceReason;
    private String sourceRefType;
    private Long sourceRefId;
    private Long sourceLedgerId;
    private Long restoredFromLotId;
    private Instant expiryNoticedAt;
    private Instant createdAt;

    protected PointLotEntity() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public PointLotKind getKind() {
        return kind;
    }

    public long getOriginalAmount() {
        return originalAmount;
    }

    public long getRemainingAmount() {
        return remainingAmount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getSourceReason() {
        return sourceReason;
    }

    public String getSourceRefType() {
        return sourceRefType;
    }

    public Long getSourceRefId() {
        return sourceRefId;
    }

    public Long getSourceLedgerId() {
        return sourceLedgerId;
    }

    public Long getRestoredFromLotId() {
        return restoredFromLotId;
    }

    public Instant getExpiryNoticedAt() {
        return expiryNoticedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** {@code now} 에 소멸일이 지났나(소멸일 ≤ 지금 — 유상은 늘 아니다) */
    public boolean expiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
