package com.community.pointshop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 장착 — {@code user_equipped}(V86), (회원, 자리)마다 보유 줄 하나. 장착은 저장소 {@code UserItemStore} 의 {@code INSERT … ON CONFLICT (user_id, slot) DO UPDATE}
 * (native — upsert), 해제는 QueryDSL DELETE.
 * {@code itemId}·{@code expiresAt} 은 보유 줄의 사본이다(결정 8 — 작성자 장식 표시가 이 표 + 품목 하나만 붙인다). 기간이 지나면 읽을 때 해제로 본다.
 */
@Entity
@Table(name = "user_equipped")
@IdClass(UserEquippedId.class)
public class UserEquippedEntity {

    @Id
    private Long userId;

    @Id
    @Enumerated(EnumType.STRING)
    private EquipSlot slot;

    private Long userItemId;
    private Long itemId;
    private Instant expiresAt;
    private Instant equippedAt;

    protected UserEquippedEntity() {
    }

    /** {@code now} 에 아직 장착으로 보이나(기간이 없거나 끝이 지금 뒤) */
    public boolean liveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public Long getUserId() {
        return userId;
    }

    public EquipSlot getSlot() {
        return slot;
    }

    public Long getUserItemId() {
        return userItemId;
    }

    public Long getItemId() {
        return itemId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getEquippedAt() {
        return equippedAt;
    }
}
