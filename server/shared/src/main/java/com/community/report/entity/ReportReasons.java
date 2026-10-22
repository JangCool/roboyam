package com.community.report.entity;

import java.util.List;

/**
 * 신고 사유 코드 — admin-web {@code BoardReportReason}·user-web {@code ReportReason} 과 같고 DB CHECK(V46 {@code ck_reports_reason})와 같다.
 * 숨김 까닭({@code posts.hidden_reason}·{@code comments.hidden_reason})도 여기 둔다.
 */
public final class ReportReasons {

    public static final String SPAM_AD = "SPAM_AD";
    public static final String ABUSE = "ABUSE";
    public static final String OBSCENE = "OBSCENE";
    /** 불법촬영물·아동성착취물 — 1건으로 즉시 숨김(끌 수 없음, 전기통신사업법 제22조의5) */
    public static final String ILLEGAL_FILMING = "ILLEGAL_FILMING";
    /** 개인정보 노출·명예훼손(권리침해) — 임시조치 기간 동안 숨김(정보통신망법 제44조의2) */
    public static final String RIGHTS = "RIGHTS";
    /** 기타 — 설명 필수 */
    public static final String OTHER = "OTHER";

    public static final List<String> ALL = List.of(SPAM_AD, ABUSE, OBSCENE, ILLEGAL_FILMING, RIGHTS, OTHER);

    /** 숨김 까닭: 신고 누적(서로 다른 회원 수) */
    public static final String HIDDEN_REPORTED = "REPORTED";
    /** 숨김 까닭: 불법촬영물 등 1건 즉시 */
    public static final String HIDDEN_ILLEGAL = "ILLEGAL";
    /** 숨김 까닭: 권리침해 임시조치(기간이 끝나면 다시 보인다) */
    public static final String HIDDEN_RIGHTS = "RIGHTS";

    private ReportReasons() {
    }
}
