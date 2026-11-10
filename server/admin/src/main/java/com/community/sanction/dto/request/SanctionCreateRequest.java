package com.community.sanction.dto.request;

/**
 * {@code POST /api/admin/sanctions} — 검사는 서비스가 한다(칸 {@code kind}·{@code reason}·{@code skipStep}·{@code confirmNickname}).
 * 익명 글·댓글(V184)이면 {@code userId} 대신 {@code anonymousPostId} 또는 {@code anonymousCommentId} — 서버가 그 작성자를 대상으로 하고(봉인 제재 — 영구 정지 닉네임 확인 없음,
 * 응답 {@code user.sealed true}), 익명이 아니면 409 {@code NOT_ANONYMOUS}, 셋 중 둘 이상이면 400(anonymous-board-contract 「API 계약」). {@code reportCaseId} 는 함께 보낼 수 있다.
 */
public record SanctionCreateRequest(String userId, String kind, String reason, Boolean skipStep, String confirmNickname, String reportCaseId,
                                    String anonymousPostId, String anonymousCommentId) {
}
