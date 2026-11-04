package com.community.sanction.dto.response;

import java.util.List;

/**
 * 회원 하나의 제재 현황(admin-web {@code SanctionPreview}) — {@code GET /api/admin/sanctions/preview} · 신고 상세 작성자 카드의 {@code sanction}.
 *
 * @param blockedReason {@code ALREADY_BANNED} · {@code SUSPENDED} · null
 * @param next          다음 단계 — 이미 영구 정지면 null
 * @param active        진행 중인 정지·영구 정지 — 없으면 null
 * @param history       이 회원의 제재(최근 것이 위, 최대 30개, 무효 포함)
 */
public record SanctionPreviewView(SanctionUserRefView user, int warningCount, String blockedReason, SanctionNextView next, List<SanctionOptionView> options,
                                  SanctionLineView active, List<SanctionLineView> history) {
}
