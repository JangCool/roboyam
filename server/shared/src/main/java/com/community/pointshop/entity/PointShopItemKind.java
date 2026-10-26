package com.community.pointshop.entity;

import java.util.Optional;

/**
 * 포인트 상점 품목 종류 — V86 {@code ck_point_shop_items_kind}·user-web·admin-web {@code PointShopItemKind} 와 같은 값(결정 9·14).
 * 종류마다 쓸 수 있는 사용 방식과 칸이 정해져 있다({@code ck_point_shop_items_kind_usage}·{@code _kind_fields}).
 */
public enum PointShopItemKind {

    /** 닉네임 변경권 — 소모형만 */
    NICKNAME_CHANGE_TICKET,
    /** 닉네임 색 — 영구·기간제, 색({@code #RRGGBB}) 필수. 장착 자리 {@link EquipSlot#NICKNAME_COLOR} */
    NICKNAME_COLOR,
    /** 프로필 테두리 — 영구·기간제, 팔레트 키 필수. 장착 자리 {@link EquipSlot#PROFILE_BORDER} */
    PROFILE_BORDER,
    /** 프로필 아바타 — 영구만, 카탈로그 ID 필수. 장착 자리를 쓰지 않고 프로필 이미지로 고른다. 관리자가 만들거나 지우지 못한다 */
    PROFILE_AVATAR;

    /** 이 종류가 쓸 수 있는 사용 방식인가 */
    public boolean allows(PointShopUsageType usage) {
        return switch (this) {
            case NICKNAME_CHANGE_TICKET -> usage == PointShopUsageType.CONSUMABLE;
            case NICKNAME_COLOR, PROFILE_BORDER -> usage == PointShopUsageType.PERMANENT || usage == PointShopUsageType.PERIOD;
            case PROFILE_AVATAR -> usage == PointShopUsageType.PERMANENT;
        };
    }

    /** 장착 자리(없으면 빈 값 — 변경권·아바타) */
    public Optional<EquipSlot> slot() {
        return switch (this) {
            case NICKNAME_COLOR -> Optional.of(EquipSlot.NICKNAME_COLOR);
            case PROFILE_BORDER -> Optional.of(EquipSlot.PROFILE_BORDER);
            default -> Optional.empty();
        };
    }
}
