package com.community.analytics.store;

import org.springframework.stereotype.Repository;

/**
 * 회원 주·월 순방문자 표시(analytics V8 — {@code analytics_member_week_marks}·{@code analytics_member_month_marks}, site-analytics-contract 「3단계」 결정 ④)의 회원 파기.
 * 회원 탈퇴가 완료되면 그 회원 번호의 표시를 지운다(처리방침 「회원 활동 표시는 탈퇴 때 파기」). 숫자 표({@code analytics_unique_counts})는 사람을 가리키지 않아 남긴다.
 *
 * <ul>
 *   <li>분석 전용 연결({@link AnalyticsDatabase#jdbc()})로 지운다 — 지금처럼 메인 DB 와 같은 풀이면 부르는 쪽(탈퇴 완료) 트랜잭션에 참여해 함께 커밋·되돌림되고,
 *       분석 DB 를 따로 떼면 그 DB 에서 바로 지워진다(메인 DB 의 탈퇴 정리 배치 SQL 로 하지 않는 까닭).</li>
 *   <li>표가 아직 없으면(analytics 스트림을 옮기는 api 보다 관리자 서버가 먼저 뜬 배포 중) 아무것도 하지 않는다 — 없는 표를 건드려 탈퇴 트랜잭션이 깨지지 않게.</li>
 *   <li>부모 표에 한 문장씩 — 기본 키 (period_start, member_id) 의 뒤 칸이지만 조각이 이번·지난 기간 몇 개뿐이라 건너뛰기 검색이 짧다.</li>
 * </ul>
 */
@Repository
public class AnalyticsMemberMarks {

    private final AnalyticsDatabase db;

    public AnalyticsMemberMarks(AnalyticsDatabase db) {
        this.db = db;
    }

    /** 그 회원의 주·달 표시를 지운다 — 지운 줄 수(표가 없으면 0) */
    public int deleteMember(long memberId) {
        Boolean ready = db.jdbc().queryForObject(
                "SELECT to_regclass('analytics_member_week_marks') IS NOT NULL AND to_regclass('analytics_member_month_marks') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(ready)) {
            return 0;
        }
        return db.jdbc().update("DELETE FROM analytics_member_week_marks WHERE member_id = ?", memberId)
                + db.jdbc().update("DELETE FROM analytics_member_month_marks WHERE member_id = ?", memberId);
    }
}
