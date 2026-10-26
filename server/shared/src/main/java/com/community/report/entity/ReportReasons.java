package com.community.report.entity;

import java.util.List;

/**
 * 신고 사유 코드 — admin-web {@code BoardReportReason}·user-web {@code ReportReason} 과 같고 DB CHECK(V46 · V95 {@code ck_reports_reason})와 같다.
 * 글·댓글 사유({@link #ALL})와 소모임 사유({@link #GROUP_ALL}, V95)는 섞어 쓰지 않는다 — {@link #forTarget}.
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

    /** 글·댓글 신고의 사유 */
    public static final List<String> ALL = List.of(SPAM_AD, ABUSE, OBSCENE, ILLEGAL_FILMING, RIGHTS, OTHER);

    /** 소모임 사유: 홍보·영업 목적(V95) */
    public static final String PROMOTION = "PROMOTION";
    /** 소모임 사유: 오프라인 모임 위험(V95) */
    public static final String OFFLINE_RISK = "OFFLINE_RISK";
    /** 소모임 사유: 종목 추천·유료 모집 — 투자 고지 게시판의 소모임만(V95) */
    public static final String STOCK_RECOMMENDATION = "STOCK_RECOMMENDATION";

    /** 소모임 신고의 사유(community docs/api-report.md 1절) — 기타는 설명 필수 */
    public static final List<String> GROUP_ALL = List.of(PROMOTION, OFFLINE_RISK, STOCK_RECOMMENDATION, OTHER);

    /** 모든 사유 — 보이는 차례(관리자 목록의 사유별 수·사유 거르기) */
    public static final List<String> EVERY = List.of(SPAM_AD, ABUSE, OBSCENE, ILLEGAL_FILMING, RIGHTS, PROMOTION, OFFLINE_RISK, STOCK_RECOMMENDATION, OTHER);

    /** 대상 종류의 사유 — 소모임이면 {@link #GROUP_ALL}, 그 밖(글·댓글)은 {@link #ALL} */
    public static List<String> forTarget(String targetType) {
        return ReportEntity.GROUP.equals(targetType) ? GROUP_ALL : ALL;
    }

    /** 숨김 까닭: 신고 누적(서로 다른 회원 수) */
    public static final String HIDDEN_REPORTED = "REPORTED";
    /** 숨김 까닭: 불법촬영물 등 1건 즉시 */
    public static final String HIDDEN_ILLEGAL = "ILLEGAL";
    /** 숨김 까닭: 권리침해 임시조치(기간이 끝나면 다시 보인다) */
    public static final String HIDDEN_RIGHTS = "RIGHTS";
    /** 숨김 까닭: 운영자가 숨김(관리자 게시글 관리 — V80). 공개 목록에는 게시판 옵션 staffHiddenNotice 가 켜졌을 때만 이 값으로 밝힌다 */
    public static final String HIDDEN_STAFF = "STAFF";

    /** 신고 자동 숨김 까닭 — 관리자 신고 관리에서 「무시」하면 풀고(복구), 「자동 숨김 확정」은 이 까닭일 때만 한다. 운영자 숨김({@link #HIDDEN_STAFF})은 들지 않는다 */
    public static final List<String> AUTO_HIDDEN = List.of(HIDDEN_REPORTED, HIDDEN_ILLEGAL, HIDDEN_RIGHTS);

    /** 신고 자동 숨김 까닭인가 */
    public static boolean isAutoHidden(String hiddenReason) {
        return hiddenReason != null && AUTO_HIDDEN.contains(hiddenReason);
    }

    private ReportReasons() {
    }
}
