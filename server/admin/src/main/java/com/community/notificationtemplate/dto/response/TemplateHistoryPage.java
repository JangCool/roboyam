package com.community.notificationtemplate.dto.response;

import java.util.List;

/** 변경 이력 한 쪽 — 최신이 위, {@code page} 는 0 부터 */
public record TemplateHistoryPage(List<TemplateHistoryItemView> items, long total, int page, int size) {
}
