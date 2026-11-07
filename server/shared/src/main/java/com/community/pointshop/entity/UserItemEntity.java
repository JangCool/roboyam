package com.community.pointshop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원이 가진 포인트 상점 아이템 한 줄(구매 하나 = 줄 하나) — {@code user_items}(V86, V157 올리기 변경권 예약 칸). 만들기는 {@link #own}(구매와 같은 트랜잭션에서 넣고 바로
 * 내보낸다). 사용·회수·예약은 저장소 {@code UserItemStore} 의 조건부 UPDATE(QueryDSL — 변경권 한 장 쓰기·예약은 {@code FOR UPDATE … SKIP LOCKED} 로 고른 한 줄을 고치는
 * native {@code UPDATE … RETURNING} 한 문장). 만료는 저장하지 않고 {@link #expiredAt} 으로 계산한다(결정 6). {@code firstUsedAt} 은 지워지지 않는다(트리거).
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
    /** 올리기 변경권이 신청에 예약된 시각(V157 — 예약 중에만) */
    private Instant reservedAt;
    /** 예약·사용한 신청 종류(V157) */
    @Enumerated(EnumType.STRING)
    private UploadTicketUse refType;
    /** 예약·사용한 신청 ID(V157) */
    private Long refId;
    /** 보상 이벤트로 받은 줄 — 그 줄 결과(V163, 구매 {@code purchaseId} 와 정확히 하나). 넣기는 저장소의 JDBC 문장(세이브포인트 안 — {@code UserItemStore#grantFromEvent}) */
    private Long grantResultId;
    /** 지급 순번(소모형 수량 N 이면 1..N) */
    private Short grantSeq;

    protected UserItemEntity() {
    }

    /** 새 보유 줄(OWNED) — 구매 하나에 하나 */
    public static UserItemEntity own(long id, long userId, long itemId, Long purchaseId, PointShopUsageType usageType, Instant startsAt, Instant expiresAt,
                                     Instant createdAt) {
        UserItemEntity ui = new UserItemEntity();
        ui.id = id;
        ui.userId = userId;
        ui.itemId = itemId;
        ui.purchaseId = purchaseId;
        ui.usageType = usageType;
        ui.status = UserItemStatus.OWNED;
        ui.startsAt = startsAt;
        ui.expiresAt = expiresAt;
        ui.createdAt = createdAt;
        return ui;
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

    public Instant getReservedAt() {
        return reservedAt;
    }

    public UploadTicketUse getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public Long getGrantResultId() {
        return grantResultId;
    }

    public Short getGrantSeq() {
        return grantSeq;
    }
}
