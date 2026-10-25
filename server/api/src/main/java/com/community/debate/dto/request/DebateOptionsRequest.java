package com.community.debate.dto.request;

/**
 * {@code POST /api/posts} 의 {@code debate} — 글과 함께 찬반 토론을 연다(회원 + 게시판 {@code debateEnabled} 일 때만, 아니면 400 {@code fields.debate}).
 *
 * @param neutralEnabled      「중립도 받기」 — 비우면 켬
 * @param stanceChangeAllowed 「입장 바꾸기 허용」 — 비우면 켬
 */
public record DebateOptionsRequest(Boolean neutralEnabled, Boolean stanceChangeAllowed) {
}
