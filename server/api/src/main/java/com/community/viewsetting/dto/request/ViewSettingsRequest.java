package com.community.viewsetting.dto.request;

/**
 * {@code PUT /api/me/settings} — user-web {@code ViewSettings}({@code { theme, textScale, commentOrder, menuPosition }}). 앞의 세 칸은 보내야 한다(통째로 바꾼다).
 * {@code menuPosition} 만은 빠지면(옛 화면) 저장된 값을 그대로 둔다. 타입이 틀린 값(문자열 {@code "100"}, 소수 {@code 100.5} 등)도 칸 오류로 돌려주려고
 * {@code Object} 로 받고, 검사는 서비스({@code ViewSettingsPolicy})가 한다.
 *
 * @param theme        {@code AUTO|LIGHT|DARK}
 * @param textScale    JSON 정수 {@code 90|100|110|125}
 * @param commentOrder {@code OLDEST|LATEST}
 * @param menuPosition 데스크톱 메뉴 위치 {@code AUTO|TOP|LEFT|RIGHT|TOP_LEFT|TOP_RIGHT} — 빠지면 그대로
 */
public record ViewSettingsRequest(Object theme, Object textScale, Object commentOrder, Object menuPosition) {
}
