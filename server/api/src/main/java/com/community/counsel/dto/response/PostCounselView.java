package com.community.counsel.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 상담 글 상세의 AI 상담 칸(user-web {@code PostCounselView} — {@code GET /api/posts/{id}/counsel}).
 *
 * @param status          WAITING · ANSWERED · PICKED · RISK
 * @param canPick         보는 사람이 사연을 쓴 사람이고 아직 고르지 않았고 고를 수 있는 사연이다(판정을 마친 위험 아닌 사연)
 * @param pickedCommentId 「가장 도움 된 조언」으로 고른 답의 댓글 ID — 없으면 null
 * @param answerDueAt     AI 답이 오는 시한(사연 등록 + 10분, UTC) — 위험 사연은 null
 * @param counselors      상담사와 답(위험 사연은 빈 목록) — 판정 전이면 지금 켜진 상담사, 판정 뒤에는 그때 답하기로 한 상담사(답을 끝내 못 한 상담사는 시한 + 50분 뒤 빠진다)
 */
public record PostCounselView(String status, boolean canPick, String pickedCommentId, Instant answerDueAt, List<CounselAiAnswerView> counselors) {
}
