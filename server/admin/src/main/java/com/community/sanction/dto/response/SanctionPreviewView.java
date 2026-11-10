package com.community.sanction.dto.response;

import java.util.List;

/**
 * 회원 하나의 제재 현황(admin-web {@code SanctionPreview}) — {@code GET /api/admin/sanctions/preview} · 신고 상세 작성자 카드의 {@code sanction}.
 *
 * @param blockedReason {@code ALREADY_BANNED} · {@code SUSPENDED} · null
 * @param next          다음 단계 — 이미 영구 정지면 null
 * @param active        진행 중인 정지·영구 정지 — 없으면 null
 * @param warningCount  유효한 경고 수 — 봉인 제재도 센다(단계 계산과 같은 수)
 * @param active        진행 중인 정지·영구 정지 — 없으면 null. 봉인 제재면 {@code active.sealed true}
 * @param history       이 회원의 제재(최근 것이 위, 최근 30개 범위, 무효 포함). 회원 대상 현황({@code userId})은 그 범위의 <b>봉인 제재 줄을 뺀다</b>(회원 화면에서 익명 글과
 *                      이어지지 않게 — anonymous-board-contract 2026-11-11). 「이 글/댓글 작성자」 현황은 모든 줄을 싣고 대상을 봉인한다
 * @param sealedCount   {@code warningCount} 에 들어간 봉인 경고 수(유효한 봉인 경고, {@code warningCount} 를 넘지 않는다) — 화면이 「경고 n회(봉인 m건 포함)」로 쓴다.
 *                      없으면 0, 「이 글/댓글 작성자」 현황은 늘 0
 */
public record SanctionPreviewView(SanctionUserRefView user, int warningCount, String blockedReason, SanctionNextView next, List<SanctionOptionView> options,
                                  SanctionLineView active, List<SanctionLineView> history, int sealedCount) {
}
