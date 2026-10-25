package com.community.viewsetting.dto.response;

/**
 * {@code GET}·{@code PUT /api/me/settings} — user-web {@code ViewSettings} 와 같은 모양. 저장한 적이 없으면 기본값.
 *
 * @param theme        야간 모드 {@code AUTO|LIGHT|DARK}
 * @param textScale    글자 크기(백분율) {@code 90|100|110|125}
 * @param commentOrder 댓글 기본 정렬 {@code OLDEST|LATEST}
 */
public record ViewSettingsResponse(String theme, int textScale, String commentOrder) {
}
