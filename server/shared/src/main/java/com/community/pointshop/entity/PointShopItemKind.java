package com.community.pointshop.entity;

import java.util.Optional;

/**
 * 포인트 상점 품목 종류 — V86 {@code ck_point_shop_items_kind}·user-web·admin-web {@code PointShopItemKind} 와 같은 값(결정 9·14).
 * 종류마다 쓸 수 있는 사용 방식과 칸이 정해져 있다({@code ck_point_shop_items_kind_usage}·{@code _kind_fields}).
 */
public enum PointShopItemKind {

    /** 닉네임 변경권 — 소모형만(상점 거르기 {@code kind=NICKNAME_CHANGE_TICKET} 은 「변경권」 탭이라 세 변경권을 함께 준다) */
    NICKNAME_CHANGE_TICKET,
    /** 닉네임 색 — 영구·기간제, 색({@code #RRGGBB}) 필수. 장착 자리 {@link EquipSlot#NICKNAME_COLOR} */
    NICKNAME_COLOR,
    /** 프로필 테두리 — 영구·기간제, 팔레트 키 필수. 장착 자리 {@link EquipSlot#PROFILE_BORDER} */
    PROFILE_BORDER,
    /** 프로필 아바타 — 영구만, 카탈로그 ID 필수. 장착 자리를 쓰지 않고 프로필 이미지로 고른다. 관리자가 만들거나 지우지 못한다 */
    PROFILE_AVATAR,
    /** 닉네임 이미지 변경권(V157 — commerce-motion-avatar-contract 규칙 0) — 소모형만. 닉네임 이미지 신청마다 한 장이 그 신청에 예약되고 승인되면 쓴 것 */
    NICKNAME_IMAGE_TICKET,
    /** 아바타 변경권(V157 — 규칙 0) — 소모형만. 아바타 올리기 신청마다 한 장이 예약되고 승인되면 쓴 것 */
    AVATAR_UPLOAD_TICKET;

    /** 변경권 종류(닉네임 변경권·닉네임 이미지 변경권·아바타 변경권) — 회원 상점의 「변경권」 탭이 함께 보인다 */
    public boolean ticket() {
        return this == NICKNAME_CHANGE_TICKET || this == NICKNAME_IMAGE_TICKET || this == AVATAR_UPLOAD_TICKET;
    }

    /** 이 종류가 쓸 수 있는 사용 방식인가 */
    public boolean allows(PointShopUsageType usage) {
        return switch (this) {
            case NICKNAME_CHANGE_TICKET, NICKNAME_IMAGE_TICKET, AVATAR_UPLOAD_TICKET -> usage == PointShopUsageType.CONSUMABLE;
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
