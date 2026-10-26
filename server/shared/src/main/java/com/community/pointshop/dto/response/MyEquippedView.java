package com.community.pointshop.dto.response;

/** {@code PUT·DELETE /api/me/equipped/{slot}} — 두 자리의 지금 장착(기간이 끝난 것은 null) */
public record MyEquippedView(EquippedNicknameColorView nicknameColor, EquippedProfileBorderView profileBorder) {
}
