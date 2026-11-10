package com.community.post.dto.response;

/**
 * 게시글 관리 줄의 작성자.
 *
 * @param kind      MEMBER · GUEST
 * @param id        회원 ID(비회원 null)
 * @param name      회원은 지금 닉네임(탈퇴·없음이면 빈 글자), 비회원은 입력한 이름
 * @param withdrawn 탈퇴·삭제된 회원인가(비회원 false)
 * @param sealed    익명 글·댓글(V184)의 작성자 — true 면 종류·ID·이름은 null, 탈퇴 false(봉인 — 작성자 열람으로만 연다, anonymous-board-contract 정책 8)
 */
public record AdminPostAuthorView(String kind, String id, String name, boolean withdrawn, boolean sealed) {

    /** 봉인된 작성자 */
    public static final AdminPostAuthorView SEALED = new AdminPostAuthorView(null, null, null, false, true);

    public AdminPostAuthorView(String kind, String id, String name, boolean withdrawn) {
        this(kind, id, name, withdrawn, false);
    }
}
