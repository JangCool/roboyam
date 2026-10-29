package com.community.home.dto.response;

import java.util.List;

/** {@code GET /api/admin/gadget-pages} — 만든 차례 */
public record GadgetPageListResponse(List<GadgetPageView> pages) {
}
