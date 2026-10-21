package com.community.site.dto.response;

/** 표시 기본값 네 칸(요청의 {@code DisplayValuesRequest} 와 같은 모양) */
public record DisplayValuesView(String language, String zoneId, String timeFormat, String dateFormat) {
}
