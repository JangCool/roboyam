package com.community.pointshop.entity;

import java.util.Arrays;
import java.util.Optional;

/** 장착 자리 — 자리마다 하나({@code user_equipped} PK). 주소의 {@code {slot}} 값이기도 하다 */
public enum EquipSlot {
    NICKNAME_COLOR(PointShopItemKind.NICKNAME_COLOR),
    PROFILE_BORDER(PointShopItemKind.PROFILE_BORDER);

    private final PointShopItemKind kind;

    EquipSlot(PointShopItemKind kind) {
        this.kind = kind;
    }

    /** 이 자리에 장착할 수 있는 품목 종류 */
    public PointShopItemKind kind() {
        return kind;
    }

    public static Optional<EquipSlot> of(String code) {
        return Arrays.stream(values()).filter(slot -> slot.name().equals(code)).findFirst();
    }
}
