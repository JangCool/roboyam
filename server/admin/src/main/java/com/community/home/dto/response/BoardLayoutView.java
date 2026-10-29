package com.community.home.dto.response;

import com.community.home.dto.HomeGridLayout;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET}·{@code PUT /api/admin/board-layouts/{theme}} — admin-web {@code BoardLayoutView}(홈 배치 모양 + 적용 화면).
 *
 * @param updatedAt 마지막 저장 시각 — 저장한 적 없으면(기본 배치 — 본문만) null
 * @param updatedBy 마지막으로 저장한 운영자 이름 — 저장한 적 없거나 운영자를 못 찾으면 null
 */
public record BoardLayoutView(String theme, BoardLayoutScreensView screens, HomeGridLayout layout, List<HomeLayoutGadgetView> gadgets, Instant updatedAt,
                              String updatedBy) {
}
