package com.community.debate.dto.request;

/**
 * {@code PUT /api/admin/debates/{postId}} — 글의 토론 열기·설정 바꾸기·닫기(docs/features/post-debate-contract.md).
 *
 * @param enabled             열기(true)·닫기(false) — 필수(비면 400 {@code fields.enabled}). 운영자는 투표가 있어도 닫는다
 * @param neutralEnabled      「중립도 받기」 — 열 때 비우면 켬, 열려 있을 때 비우면 그대로
 * @param stanceChangeAllowed 「입장 바꾸기 허용」 — 같은 규칙
 */
public record DebateSaveRequest(Boolean enabled, Boolean neutralEnabled, Boolean stanceChangeAllowed) {
}
