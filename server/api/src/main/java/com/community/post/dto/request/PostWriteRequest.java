package com.community.post.dto.request;

import com.community.board.dto.request.GuestCredentials;
import com.community.debate.dto.request.DebateOptionsRequest;
import com.community.poll.dto.request.PollInput;

/**
 * {@code POST /api/posts}(user-web {@code PostWriteRequest}). 검사 순서가 계약(게시판 → 쓰기 판정 → 칸 → 금지어 → 캡차 → 속도)이라 Bean Validation 없이 서비스가 본다.
 *
 * @param prefixId 말머리 ID(문자열), 없으면 null
 * @param contentFormat 본문 형식 TEXT · HTML — 빠지면 TEXT(예전 클라이언트). 평문 게시판(글 에디터 NONE)에 HTML 이면 400 {@code fields.contentFormat}
 * @param guest    비회원이면 필수, 회원이면 null(보내도 무시한다)
 * @param debate   있으면 글과 함께 찬반 토론을 연다 — 회원이고 게시판 정책 {@code debateEnabled} 일 때만(아니면 400 {@code fields.debate}). null 이면 열지 않는다
 * @param poll     있으면 글에 투표를 붙인다 — 회원이고 게시판 정책 {@code pollEnabled} 일 때만(아니면 400 {@code fields.poll}), 칸 오류는 {@code fields."poll.*"}.
 *                 null 이면 붙이지 않는다(docs/features/post-poll-contract.md)
 */
public record PostWriteRequest(String boardSlug, String prefixId, String title, String contentFormat, String content, Boolean adult, GuestCredentials guest,
                               DebateOptionsRequest debate, PollInput poll) {
}
