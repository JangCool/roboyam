package com.community.home.dto.response;

import com.community.home.dto.HomeGridLayout;
import java.util.List;

/** {@code GET /api/admin/board-layouts/{theme}/defaults} — 기본 배치(본문만 전체 폭, 두 화면 켬 — 저장하지 않는다). admin-web {@code BoardLayoutBody} */
public record BoardLayoutBodyView(BoardLayoutScreensView screens, HomeGridLayout layout, List<HomeLayoutGadgetView> gadgets) {
}
