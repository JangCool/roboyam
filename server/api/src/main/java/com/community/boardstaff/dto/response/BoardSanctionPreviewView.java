package com.community.boardstaff.dto.response;

import java.time.Instant;

/**
 * {@code GET /api/board-staff/sanctions/preview}(user-web {@code BoardSanctionPreview}) — 제재 대화창의 현황.
 *
 * @param recommended 권장 단계(유효한 제재 수로 계산: 경고 → 1일 → 3일 → 7일, 그 뒤 7일) — 안내일 뿐 막지 않는다
 */
public record BoardSanctionPreviewView(BoardSanctionTargetView target, BoardSanctionCountsView counts, BoardSanctionActiveView activeRestriction, String recommended,
                                       BoardStaffQuotaView quota, Instant resetsAt) {
}
