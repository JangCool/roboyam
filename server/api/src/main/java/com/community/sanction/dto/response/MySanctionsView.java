package com.community.sanction.dto.response;

import java.util.List;

/**
 * {@code GET /api/me/sanctions}(user-web {@code MySanctions}).
 *
 * @param status       계정 상태(ACTIVE · SUSPENDED · BANNED — 탈퇴 진행 중이면 WITHDRAWING)
 * @param warningCount 유효한 경고 수(0~2)
 * @param current      진행 중인 정지·영구 정지(없으면 null)
 * @param items        이력 — 최근 것이 위, 최대 50개, 무효 포함
 */
public record MySanctionsView(String status, int warningCount, MySanctionView current, List<MySanctionView> items) {
}
