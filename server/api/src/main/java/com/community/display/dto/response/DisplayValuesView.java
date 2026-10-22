package com.community.display.dto.response;

/**
 * 표시 값 네 칸 — user-web {@code DisplayValues}(공유 레이어 {@code @roboyam/display-time})와 같은 모양.
 *
 * @param language   {@code ko} · {@code en}
 * @param zoneId     IANA 시간대 이름
 * @param timeFormat {@code H24} · {@code H12}
 * @param dateFormat {@code YMD_DASH} · {@code YMD_DOT} · {@code DMY_SLASH} · {@code MDY_SLASH}
 */
public record DisplayValuesView(String language, String zoneId, String timeFormat, String dateFormat) {
}
