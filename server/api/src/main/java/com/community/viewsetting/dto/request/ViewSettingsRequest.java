package com.community.viewsetting.dto.request;

/**
 * {@code PUT /api/me/settings} — user-web {@code ViewSettings}({@code { theme, textScale, commentOrder }}). 세 칸 모두 보내야 한다(통째로 바꾼다).
 * 타입이 틀린 값(문자열 {@code "100"}, 소수 {@code 100.5} 등)도 칸 오류로 돌려주려고 {@code Object} 로 받고, 검사는 서비스({@code ViewSettingsPolicy})가 한다.
 *
 * @param theme        {@code AUTO|LIGHT|DARK}
 * @param textScale    JSON 정수 {@code 90|100|110|125}
 * @param commentOrder {@code OLDEST|LATEST}
 */
public record ViewSettingsRequest(Object theme, Object textScale, Object commentOrder) {
}
