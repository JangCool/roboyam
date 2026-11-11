package com.community.grade.dto.response;

/**
 * {@code GET /api/me/grade}(user-web {@code MyGrade}).
 *
 * @param autoPromotion ACTIVE · PAUSED_SANCTION(정지 중 — 우선) · PAUSED_ADMIN(운영자가 올려 둔 회원이고 등급 옵션이 「자동 승급 중지」)
 * @param next          가장 높은 등급이면 null
 * @param level         레벨(V196 — grade-level-contract 4·5절)
 * @param xp            누적 경험치
 * @param levelXp       이 레벨이 시작한 누적 경험치
 * @param nextLevelXp   다음 레벨이 되는 누적 경험치
 */
public record MyGradeResponse(GradeRefView grade, String autoPromotion, NextGradeView next, int level, long xp, long levelXp, long nextLevelXp) {
}
