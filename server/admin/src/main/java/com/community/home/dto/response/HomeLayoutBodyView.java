package com.community.home.dto.response;

import com.community.home.dto.HomeGridLayout;
import java.util.List;

/** {@code GET /api/admin/home-layouts/{theme}/defaults} — 테마 기본 배치(저장하지 않는다). admin-web {@code HomeLayoutBody} */
public record HomeLayoutBodyView(HomeGridLayout layout, List<HomeLayoutGadgetView> gadgets) {
}
