package com.community.grade.dto.response;

/**
 * {@code GET /api/me/grade}(user-web {@code MyGrade}).
 *
 * @param autoPromotion ACTIVE · PAUSED_SANCTION(정지 중 — 우선) · PAUSED_ADMIN(운영자가 올려 둔 회원이고 등급 옵션이 「자동 승급 중지」)
 * @param next          가장 높은 등급이면 null
 */
public record MyGradeResponse(GradeRefView grade, String autoPromotion, NextGradeView next) {
}
