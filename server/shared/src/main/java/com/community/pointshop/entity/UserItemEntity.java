package com.community.pointshop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원이 가진 포인트 상점 아이템 한 줄(구매 하나 = 줄 하나) — {@code user_items}(V86). <b>읽기 전용 매핑</b>(보유·장착·사용·회수는 서비스가 JDBC 로 —
 * 변경권 한 장 쓰기는 {@code SKIP LOCKED} 조건부 UPDATE). 만료는 저장하지 않고 {@link #expiredAt} 으로 계산한다(결정 6). {@code firstUsedAt} 은 지워지지 않는다(트리거).
 */
@Entity
@Table(name = "user_items")
public class UserItemEntity {

    @Id
    private Long id;

    private Long userId;
    private Long itemId;
    private Long purchaseId;
    @Enumerated(EnumType.STRING)
    private PointShopUsageType usageType;
    @Enumerated(EnumType.STRING)
    private UserItemStatus status;
    private Instant startsAt;
    private Instant expiresAt;
    private Instant firstUsedAt;
    private Instant usedAt;
    private Instant revokedAt;
    private Instant createdAt;

    protected UserItemEntity() {
    }

    /** 기간제의 기간이 {@code now} 에 끝났나(끝 ≤ 지금) */
    public boolean expiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getPurchaseId() {
        return purchaseId;
    }

    public PointShopUsageType getUsageType() {
        return usageType;
    }

    public UserItemStatus getStatus() {
        return status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getFirstUsedAt() {
        return firstUsedAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
