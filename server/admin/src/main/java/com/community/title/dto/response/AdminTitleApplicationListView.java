package com.community.title.dto.response;

import java.util.List;

/** 칭호 신청 목록({@code GET /api/admin/titles/applications} — admin-web {@code AdminTitleApplicationList}) — 최신 순 200개까지 */
public record AdminTitleApplicationListView(List<AdminTitleApplicationView> items) {
}
