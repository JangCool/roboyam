package com.community.profile.dto.request;

import com.community.site.dto.request.DisplayValuesRequest;

/**
 * 내 표시 형식 저장 — 「내 표시 형식을 쓴다」와 값 네 칸. 꺼도 값은 남으므로 값은 늘 함께 보낸다. 칸별 검사는 서비스가 한다({@code fields.use|language|zoneId|timeFormat|dateFormat}).
 *
 * @param use 내 표시 형식을 쓰는가. 없으면(null) 400
 */
public record MyDisplayRequest(Boolean use, DisplayValuesRequest values) {
}
