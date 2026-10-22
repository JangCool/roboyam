package com.community.report.repository.master;

import com.community.report.entity.QReportEntity;
import com.community.report.entity.ReportEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 신고 정책 판정에 쓰는 집계 — 하루 수·마지막 시각·반려 누적·대상별 서로 다른 신고자 수 — 와 관리자 회원 상세의 신고 요약 */
@Repository
public class ReportRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QReportEntity REPORT = QReportEntity.reportEntity;

    public ReportRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 이 회원이 {@code since} 뒤에 한 신고 수 */
    public long countSince(long reporterId, Instant since) {
        Long n = query.select(REPORT.id.count()).from(REPORT).where(REPORT.reporterId.eq(reporterId), REPORT.createdAt.goe(since)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 이 회원의 마지막 신고 시각 — 없으면 null */
    public Instant lastAt(long reporterId) {
        return query.select(REPORT.createdAt.max()).from(REPORT).where(REPORT.reporterId.eq(reporterId)).fetchOne();
    }

    /** 이 회원의 신고가 {@code since} 뒤에 반려된 시각들(최신순) */
    public List<Instant> rejectedSince(long reporterId, Instant since) {
        return query.select(REPORT.resolvedAt).from(REPORT)
                .where(REPORT.reporterId.eq(reporterId), REPORT.status.eq(ReportEntity.REJECTED), REPORT.resolvedAt.goe(since))
                .orderBy(REPORT.resolvedAt.desc())
                .fetch();
    }

    /** 대상 하나의 자동 숨김 셈 — 셈에 넣는(최소 등급 이상) 신고의 서로 다른 신고자 수. 한 회원은 한 대상을 한 번만 신고하므로 행 수와 같다 */
    public long countedReporters(String targetType, long targetId) {
        Long n = query.select(REPORT.reporterId.countDistinct()).from(REPORT)
                .where(REPORT.targetType.eq(targetType), REPORT.targetId.eq(targetId), REPORT.counted.isTrue())
                .fetchOne();
        return n == null ? 0 : n;
    }

    /** 회원 한 명의 신고 요약 — 받은 신고 수, 그중 조치된(수용) 수, 이 회원이 한 신고 수 */
    public record MemberReportCounts(long received, long actioned, long filed) {
    }

    /**
     * 회원 한 명의 신고 요약(관리자 회원 상세). 받은 신고 = 이 회원이 대상 작성자({@code target_author_id})인 신고, 조치 = 그중 {@code ACCEPTED},
     * 한 신고 = {@code reporter_id}. 색인: {@code ix_reports_target_author}(V48)·{@code ix_reports_reporter}(V46). 보유기간 배치가 지운 신고는 세지 않는다.
     */
    public MemberReportCounts countsOf(long userId) {
        Long received = query.select(REPORT.id.count()).from(REPORT).where(REPORT.targetAuthorId.eq(userId)).fetchOne();
        Long actioned = query.select(REPORT.id.count()).from(REPORT)
                .where(REPORT.targetAuthorId.eq(userId), REPORT.status.eq(ReportEntity.ACCEPTED)).fetchOne();
        Long filed = query.select(REPORT.id.count()).from(REPORT).where(REPORT.reporterId.eq(userId)).fetchOne();
        return new MemberReportCounts(received == null ? 0 : received, actioned == null ? 0 : actioned, filed == null ? 0 : filed);
    }

    /** 대상 하나에 들어온 신고 수(셈과 무관) */
    public long countForTarget(String targetType, long targetId) {
        Long n = query.select(REPORT.id.count()).from(REPORT).where(REPORT.targetType.eq(targetType), REPORT.targetId.eq(targetId)).fetchOne();
        return n == null ? 0 : n;
    }
}
