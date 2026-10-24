package com.community.report.repository.master;

import com.community.post.entity.QPostEntity;
import com.community.report.entity.QReportCaseEntity;
import com.community.report.entity.QReportEntity;
import com.community.report.entity.ReportCaseEntity;
import com.community.user.entity.QProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 관리자 신고 관리의 사건 질의 — 목록(거르기·정렬·쪽), 열린 사건 요약, 작성자 카드의 닫힌 사건 수. docs/features/report-admin-contract.md.
 * 색인: 상태 + 마지막 신고({@code ix_report_cases_status_last}) · 마지막 신고({@code ix_report_cases_last}) · 글({@code ix_report_cases_post}) · 작성자({@code ix_report_cases_author_closed}).
 */
@Repository
public class AdminReportRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QReportCaseEntity CASE = QReportCaseEntity.reportCaseEntity;
    private static final QReportEntity REPORT = QReportEntity.reportEntity;

    public static final String SORT_NEWEST = "newest";
    public static final String SORT_OLDEST = "oldest";
    public static final String SORT_MOST = "most";

    public AdminReportRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 목록 조건 — 값이 null 이면 거르지 않는다.
     *
     * @param statuses   사건 상태들(null = 전체)
     * @param reason     그 사유의 신고가 하나라도 있는 사건
     * @param title      대상 글 제목(댓글 사건이면 그 댓글의 글) 부분 일치
     * @param author     대상 작성자 닉네임·비회원 이름(신고 당시 이름) 부분 일치
     * @param caseNo     사건 번호 정확히
     * @param from       마지막 신고 시각 이후(포함)
     * @param to         마지막 신고 시각 이전(포함)
     * @param postId     그 글과 그 글의 댓글 사건
     * @param none       아무 사건도 맞지 않는 조건(깨진 ID 등)
     */
    public record Search(List<String> statuses, String targetType, String reason, String title, String author, Long caseNo, Instant from, Instant to,
                         Long boardId, Long postId, String sort, boolean none) {
    }

    public record ListPage(List<ReportCaseEntity> items, long total) {
    }

    private static BooleanBuilder where(Search c) {
        BooleanBuilder where = new BooleanBuilder();
        if (c.statuses() != null) {
            where.and(CASE.status.in(c.statuses()));
        }
        if (c.targetType() != null) {
            where.and(CASE.targetType.eq(c.targetType()));
        }
        if (c.reason() != null) {
            where.and(JPAExpressions.selectOne().from(REPORT).where(REPORT.caseId.eq(CASE.id), REPORT.reason.eq(c.reason())).exists());
        }
        if (c.title() != null) {
            QPostEntity post = QPostEntity.postEntity;
            where.and(CASE.postId.in(JPAExpressions.select(post.id).from(post).where(post.title.containsIgnoreCase(c.title()))));
        }
        if (c.author() != null) {
            QProfileEntity profile = QProfileEntity.profileEntity;
            where.and(CASE.targetAuthorId.in(JPAExpressions.select(profile.userId).from(profile).where(profile.nickname.containsIgnoreCase(c.author())))
                    .or(CASE.targetAuthorId.isNull().and(JPAExpressions.selectOne().from(REPORT)
                            .where(REPORT.caseId.eq(CASE.id), REPORT.snapshotAuthorName.containsIgnoreCase(c.author())).exists())));
        }
        if (c.caseNo() != null) {
            where.and(CASE.caseNo.eq(c.caseNo()));
        }
        if (c.from() != null) {
            where.and(CASE.lastReportedAt.goe(c.from()));
        }
        if (c.to() != null) {
            where.and(CASE.lastReportedAt.loe(c.to()));
        }
        if (c.boardId() != null) {
            where.and(CASE.boardId.eq(c.boardId()));
        }
        if (c.postId() != null) {
            where.and(CASE.postId.eq(c.postId()));
        }
        return where;
    }

    private static OrderSpecifier<?>[] order(String sort) {
        return switch (sort) {
            case SORT_OLDEST -> new OrderSpecifier<?>[] {CASE.lastReportedAt.asc(), CASE.id.asc()};
            case SORT_MOST -> new OrderSpecifier<?>[] {CASE.reportCount.desc(), CASE.lastReportedAt.desc(), CASE.id.desc()};
            default -> new OrderSpecifier<?>[] {CASE.lastReportedAt.desc(), CASE.id.desc()};
        };
    }

    /** 목록 한 쪽 */
    public ListPage search(Search c, long offset, int limit) {
        if (c.none()) {
            return new ListPage(List.of(), 0);
        }
        BooleanBuilder where = where(c);
        Long total = query.select(CASE.id.count()).from(CASE).where(where).fetchOne();
        if (total == null || total == 0 || offset >= total) {
            return new ListPage(List.of(), total == null ? 0 : total);
        }
        return new ListPage(query.selectFrom(CASE).where(where).orderBy(order(c.sort())).offset(offset).limit(limit).fetch(), total);
    }

    /** 사건의 대상(엔티티를 읽지 않는다 — 대상 단위 잠금을 먼저 잡고 사건 행을 잠가 읽으려고) */
    public record CaseTarget(String targetType, long targetId) {
    }

    /** 사건의 대상 — 없는 사건이면 null */
    public CaseTarget targetOf(long caseId) {
        Tuple t = query.select(CASE.targetType, CASE.targetId).from(CASE).where(CASE.id.eq(caseId)).fetchOne();
        return t == null ? null : new CaseTarget(t.get(CASE.targetType), t.get(CASE.targetId));
    }

    /** 열린 사건 수 — [처리 대기, 검토 중] */
    public long[] openCounts() {
        long pending = 0;
        long inReview = 0;
        for (Tuple t : query.select(CASE.status, CASE.id.count()).from(CASE).where(CASE.status.in(ReportCaseEntity.OPEN)).groupBy(CASE.status).fetch()) {
            Long n = t.get(1, Long.class);
            if (ReportCaseEntity.PENDING.equals(t.get(0, String.class))) {
                pending = n == null ? 0 : n;
            } else {
                inReview = n == null ? 0 : n;
            }
        }
        return new long[] {pending, inReview};
    }

    /** 이 회원이 대상 작성자인 사건 중 {@code since} 뒤에 닫힌 수 — [조치, 무시] */
    public long[] closedCountsOf(long authorId, Instant since) {
        long resolved = 0;
        long dismissed = 0;
        for (Tuple t : query.select(CASE.status, CASE.id.count()).from(CASE)
                .where(CASE.targetAuthorId.eq(authorId), CASE.closedAt.goe(since)).groupBy(CASE.status).fetch()) {
            Long n = t.get(1, Long.class);
            if (ReportCaseEntity.RESOLVED.equals(t.get(0, String.class))) {
                resolved = n == null ? 0 : n;
            } else if (ReportCaseEntity.DISMISSED.equals(t.get(0, String.class))) {
                dismissed = n == null ? 0 : n;
            }
        }
        return new long[] {resolved, dismissed};
    }
}
