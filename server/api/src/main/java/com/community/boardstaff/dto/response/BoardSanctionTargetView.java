package com.community.boardstaff.dto.response;

/**
 * 제재 대상 회원 — 닉네임은 읽을 때의 값(user-web {@code BoardSanctionTarget}).
 *
 * @param sealed 「이 글/댓글 작성자」로 건 제재(익명 — V184)면 true — 회원 ID·닉네임은 null(봉인, anonymous-board-contract 정책 9)
 */
public record BoardSanctionTargetView(String userId, String nickname, boolean sealed) {

    /** 봉인된 대상 */
    public static final BoardSanctionTargetView SEALED = new BoardSanctionTargetView(null, null, true);
}
