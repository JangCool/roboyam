package com.community.post.dto.request;

import com.community.board.dto.request.GuestCredentials;

/**
 * {@code POST /api/posts}(user-web {@code PostWriteRequest}). 검사 순서가 계약(게시판 → 쓰기 판정 → 칸 → 금지어 → 캡차 → 속도)이라 Bean Validation 없이 서비스가 본다.
 *
 * @param prefixId 말머리 ID(문자열), 없으면 null
 * @param guest    비회원이면 필수, 회원이면 null(보내도 무시한다)
 */
public record PostWriteRequest(String boardSlug, String prefixId, String title, String content, Boolean adult, GuestCredentials guest) {
}
