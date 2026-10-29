package com.community.home.dto.response;

import com.community.home.dto.HomeGridLayout;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET}·{@code PUT /api/admin/gadget-pages/{id}/layouts/{theme}} — admin-web {@code GadgetPageLayoutView}(홈 배치와 같은 모양 + 페이지 ID).
 *
 * @param updatedAt 이 테마에서 마지막 저장 시각 — 저장한 적 없으면(처음 배치) null
 */
public record GadgetPageLayoutView(String pageId, String theme, HomeGridLayout layout, List<HomeLayoutGadgetView> gadgets, Instant updatedAt, String updatedBy) {
}
