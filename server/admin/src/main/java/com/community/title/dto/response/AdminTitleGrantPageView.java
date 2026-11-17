package com.community.title.dto.response;

import java.util.List;

/** 주고 뺀 기록 한 쪽({@code GET /api/admin/titles/grants?key&memberId&page&size}) — {@code page} 0부터, {@code size} 기본 20·최대 100, {@code total} 전체 줄 수 */
public record AdminTitleGrantPageView(List<AdminTitleGrantView> items, int page, int size, long total) {
}
