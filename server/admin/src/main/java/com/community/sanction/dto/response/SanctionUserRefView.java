package com.community.sanction.dto.response;

/**
 * 제재 대상 회원 — {@code grade} 등급 키(모르면 null), {@code status} 회원 관리의 계정 상태와 같은 값.
 * {@code sealed} 「이 글/댓글 작성자」로 건 제재(익명 — V184, anonymous-board-contract 정책 9)면 true 이고 나머지 칸은 null(봉인 — 열람 권한이 있어도 제재 화면에서는 봉인).
 */
public record SanctionUserRefView(String id, String nickname, String grade, String status, boolean sealed) {

    /** 봉인된 대상 */
    public static final SanctionUserRefView SEALED = new SanctionUserRefView(null, null, null, null, true);

    public SanctionUserRefView(String id, String nickname, String grade, String status) {
        this(id, nickname, grade, status, false);
    }
}
