package com.community.home.dto.response;

import java.util.List;

/** {@code GET /api/admin/gadget-pages/folders} — 기준 폴더로 고를 폴더들(트리 차례) */
public record GadgetPageFolderListResponse(List<GadgetPageFolderView> folders) {
}
