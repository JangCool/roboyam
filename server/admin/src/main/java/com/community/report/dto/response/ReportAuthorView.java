package com.community.report.dto.response;

/**
 * 대상 작성자 — {@code kind} MEMBER · GUEST, 비회원은 id null·이름은 그때 이름. {@code sealed} 익명 글·댓글(V184)이면 true 이고 종류·ID·이름 null · 탈퇴 false(봉인 —
 * anonymous-board-contract 정책 8)
 */
public record ReportAuthorView(String kind, String id, String name, boolean withdrawn, boolean sealed) {

    /** 봉인된 작성자 */
    public static final ReportAuthorView SEALED = new ReportAuthorView(null, null, null, false, true);

    public ReportAuthorView(String kind, String id, String name, boolean withdrawn) {
        this(kind, id, name, withdrawn, false);
    }
}
