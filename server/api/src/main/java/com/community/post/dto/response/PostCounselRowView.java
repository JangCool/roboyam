package com.community.post.dto.response;

/**
 * 글 줄의 AI 연애상담소 표시(user-web {@code PostCounselRow} — ai-love-counsel-contract). 「AI 상담소」 말머리 글만, 그 밖은 줄의 {@code counsel} 이 null.
 *
 * @param status        WAITING(AI 답이 아직 없다) · ANSWERED(AI 답이 있다) · PICKED(사연 쓴 사람이 조언을 골랐다) · RISK(위험 사연 — 화면은 칩을 그리지 않는다)
 * @param aiAnswerCount 지금까지 달린 상담사 AI 답 수(0~3, 지운 답 제외)
 */
public record PostCounselRowView(String status, int aiAnswerCount) {
}
