package com.community.viewsetting.dto.response;

/**
 * {@code GET}·{@code PUT /api/me/settings} — user-web {@code ViewSettings} 와 같은 모양. 저장한 적이 없으면 기본값.
 *
 * @param theme        야간 모드 {@code AUTO|LIGHT|DARK}
 * @param textScale    글자 크기(백분율) {@code 90|100|110|125}
 * @param commentOrder 댓글 기본 정렬 {@code OLDEST|LATEST}
 * @param menuPosition 데스크톱 메뉴 위치 {@code AUTO}(운영자 설정 > 테마 기본) {@code |TOP|LEFT|RIGHT|TOP_LEFT|TOP_RIGHT}
 */
public record ViewSettingsResponse(String theme, int textScale, String commentOrder, String menuPosition) {
}
