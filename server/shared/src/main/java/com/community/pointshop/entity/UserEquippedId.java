package com.community.pointshop.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link UserEquippedEntity} 의 복합 키(회원 + 자리) */
public class UserEquippedId implements Serializable {

    private Long userId;
    private EquipSlot slot;

    protected UserEquippedId() {
    }

    public UserEquippedId(Long userId, EquipSlot slot) {
        this.userId = userId;
        this.slot = slot;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserEquippedId that && Objects.equals(userId, that.userId) && slot == that.slot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, slot);
    }
}
