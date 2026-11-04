package com.community.boardstaff.dto.response;

/** {@code viewer.staffTools.abilities}(user-web {@code BoardStaffAbilities}) — 감추는 것은 편의이고 막는 것은 처리 API 다(처리할 때마다 다시 판정) */
public record BoardStaffAbilitiesView(boolean postHide, boolean postDelete, boolean commentDelete, boolean sanction, boolean restore) {
}
