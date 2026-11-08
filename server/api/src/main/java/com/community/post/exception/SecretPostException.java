package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 403 — 비밀글인데 보는 사람이 글쓴이·그 게시판 매니저·스텝·운영자 회원 계정이 아니다(V166). 글 상세·댓글·추천·반응·신고 등 그 글에 닿는 공개 API 가 같은 코드를 낸다.
 * 오류 본문 최상위 {@code boardSlug} — 화면이 「게시판으로 돌아가기」를 그린다(제목은 목록에 이미 보이므로 게시판을 알려도 새로 드러나는 것이 없다).
 */
public class SecretPostException extends CommonException {

    public SecretPostException(String boardSlug) {
        super(HttpStatus.FORBIDDEN, "SECRET_POST", new MessageRef("error.SECRET_POST"));
        if (boardSlug != null) {
            extra("boardSlug", boardSlug);
        }
    }
}
