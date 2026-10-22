package com.community.display.dto.request;

import com.community.site.dto.request.DisplayValuesRequest;

/**
 * {@code PUT /api/me/display} — user-web {@code MyDisplayRequest}({@code { values }}). 칸별 검사는 서비스가 한다.
 * 시간대·시간 형식·날짜 형식은 꼭 보내야 하고({@code fields.zoneId|timeFormat|dateFormat}), 언어는 빼면 지금 값(없으면 사이트 기본 언어)을 그대로 둔다.
 *
 * @param values 표시 값. 없으면(null) 세 칸 모두 오류
 */
public record MyDisplayRequest(DisplayValuesRequest values) {
}
