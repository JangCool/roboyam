package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 수량 이동 줄(결정 82) — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_qty_movements")
public class GroupBuyQtyMovementEntity {

    @Id
    private Long id;
    private Long groupBuyId;
    private Long skuId;
    private Long participationId;
    private Long participationItemId;
    @Enumerated(EnumType.STRING)
    private QtyMovementKind kind;
    private Integer qty;
    private Integer currentAfter;
    private Integer reservedAfter;
    private Integer maxAfter;
    private Integer skuCurrentAfter;
    private Integer skuReservedAfter;
    private Integer skuMaxAfter;
    private String reason;
    private Long actorId;
    private Instant createdAt;

    protected GroupBuyQtyMovementEntity() {
    }

    /** 수량 이동 한 줄 — 공구 이후 값(current·reserved·max)은 공구 행을 바꾼 이동만, 단품 이후 값은 단품 이동만(없으면 null) */
    public static GroupBuyQtyMovementEntity create(long id, long groupBuyId, Long skuId, Long participationId, Long participationItemId, QtyMovementKind kind, int qty,
                                                   Integer currentAfter, Integer reservedAfter, Integer maxAfter, Integer skuCurrentAfter, Integer skuReservedAfter,
                                                   Integer skuMaxAfter, String reason, Long actorId, Instant now) {
        GroupBuyQtyMovementEntity m = new GroupBuyQtyMovementEntity();
        m.id = id;
        m.groupBuyId = groupBuyId;
        m.skuId = skuId;
        m.participationId = participationId;
        m.participationItemId = participationItemId;
        m.kind = kind;
        m.qty = qty;
        m.currentAfter = currentAfter;
        m.reservedAfter = reservedAfter;
        m.maxAfter = maxAfter;
        m.skuCurrentAfter = skuCurrentAfter;
        m.skuReservedAfter = skuReservedAfter;
        m.skuMaxAfter = skuMaxAfter;
        m.reason = reason;
        m.actorId = actorId;
        m.createdAt = now;
        return m;
    }
}
