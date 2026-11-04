package com.community.sanction.dto.response;

import java.util.List;

/** {@code GET /api/admin/sanctions} */
public record SanctionListView(SanctionCountsView counts, long total, int page, int size, List<SanctionLineView> items) {
}
