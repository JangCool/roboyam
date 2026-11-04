package com.community.sanction.dto.response;

import java.util.List;

/** {@code GET /api/admin/appeals} */
public record AppealListView(AppealCountsView counts, long total, int page, int size, List<AppealItemView> items) {
}
