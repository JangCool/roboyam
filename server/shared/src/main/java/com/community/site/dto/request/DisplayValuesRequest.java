package com.community.site.dto.request;

/**
 * 표시 기본값 네 칸 — 허용 값은 공유 레이어(apps-packages/display-time)와 같다. 칸마다 검사 문구가 나가므로 서비스가 한꺼번에 검사한다.
 *
 * @param language   {@code ko} · {@code en}
 * @param zoneId     IANA 시간대 이름(예: {@code Asia/Seoul}, 64자 이하)
 * @param timeFormat {@code H24} · {@code H12}
 * @param dateFormat {@code YMD_DASH} · {@code YMD_DOT} · {@code DMY_SLASH} · {@code MDY_SLASH}
 */
public record DisplayValuesRequest(String language, String zoneId, String timeFormat, String dateFormat) {
}
