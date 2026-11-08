package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 비밀글인데 보는 사람이 읽을 수 없다(V166 · V168). 글 상세·댓글·추천·반응·신고 등 그 글에 닿는 공개 API 가 같은 코드를 낸다.
 * 오류 본문 최상위 덧붙임:
 * <ul>
 *   <li>{@code boardSlug} — 화면이 「게시판으로 돌아가기」를 그린다(제목은 목록에 이미 보이므로 게시판을 알려도 새로 드러나는 것이 없다).</li>
 *   <li>{@code guestPost} — 비회원 글이면 true(비밀번호로 열 수 있다 — {@code POST /api/posts/{id}/secret-unlock}), 회원 글이면 false.</li>
 *   <li>{@code groupId} — 소모임 글이면 그 소모임(화면의 [목록으로]가 소모임으로 간다), 일반 글이면 칸이 없다.</li>
 * </ul>
 */
public class SecretPostException extends CommonException {

    public SecretPostException(String boardSlug, boolean guestPost, Long groupId) {
        super(HttpStatus.FORBIDDEN, "SECRET_POST", new MessageRef("error.SECRET_POST"));
        if (boardSlug != null) {
            extra("boardSlug", boardSlug);
        }
        extra("guestPost", guestPost);
        if (groupId != null) {
            extra("groupId", Long.toString(groupId));
        }
    }
}
