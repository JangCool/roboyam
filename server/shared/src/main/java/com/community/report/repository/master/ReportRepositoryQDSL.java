package com.community.report.repository.master;

import com.community.report.entity.QReportEntity;
import com.community.report.entity.ReportEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.Tuple;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 신고 정책 판정에 쓰는 집계 — 하루 수·마지막 시각·반려(허위 표시) 누적·사건별 서로 다른 신고자 수 — 와 관리자 회원 상세의 신고 요약, 관리자 신고 관리의 사건별 신고 */
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

    /**
     * 사건 하나의 자동 숨김 셈 — 셈에 넣는(최소 등급 이상) 신고의 서로 다른 신고자 수. <b>열린 사건의 신고만</b> 센다(닫힌 사건 뒤의 새 신고는 새 사건으로 0부터 — V82).
     * 한 회원은 한 대상을 한 번만 신고하므로 행 수와 같다.
     */
    public long countedReporters(long caseId) {
        Long n = query.select(REPORT.reporterId.countDistinct()).from(REPORT)
                .where(REPORT.caseId.eq(caseId), REPORT.counted.isTrue())
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

    // ── 사건(관리자 신고 관리 — docs/features/report-admin-contract.md) ──────────

    /** 사건의 신고들 — 오래된 것부터 */
    public List<ReportEntity> ofCase(long caseId) {
        return query.selectFrom(REPORT).where(REPORT.caseId.eq(caseId)).orderBy(REPORT.createdAt.asc(), REPORT.id.asc()).fetch();
    }

    /** 사건의 첫 신고(신고 당시 스냅샷) — 보유기간 배치가 지웠으면 null */
    public ReportEntity firstOfCase(long caseId) {
        return query.selectFrom(REPORT).where(REPORT.caseId.eq(caseId)).orderBy(REPORT.createdAt.asc(), REPORT.id.asc()).limit(1).fetchFirst();
    }

    /** 사건의 신고자들(겹치지 않게) — 결과 알림 */
    public List<Long> reporterIds(long caseId) {
        return query.select(REPORT.reporterId).distinct().from(REPORT).where(REPORT.caseId.eq(caseId)).fetch();
    }

    /** 사건마다 사유별 신고 수 — 사건 ID → (사유 → 수), 사유는 {@link com.community.report.entity.ReportReasons#ALL} 차례로 담지 않는다(부르는 쪽이 정렬) */
    public Map<Long, Map<String, Long>> reasonsByCase(Collection<Long> caseIds) {
        Map<Long, Map<String, Long>> out = new HashMap<>();
        if (caseIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(REPORT.caseId, REPORT.reason, REPORT.id.count()).from(REPORT)
                .where(REPORT.caseId.in(caseIds)).groupBy(REPORT.caseId, REPORT.reason).fetch()) {
            Long count = t.get(2, Long.class);
            out.computeIfAbsent(t.get(0, Long.class), k -> new LinkedHashMap<>()).put(t.get(1, String.class), count == null ? 0 : count);
        }
        return out;
    }

    /**
     * 사건을 닫거나 다시 열 때 그 신고들의 상태를 함께 바꾼다 — 조치 {@code ACCEPTED} · 허위 표시 무시 {@code REJECTED} · 그냥 무시 {@code DISMISSED} · 다시 열기 {@code PENDING}
     * ({@code at}·{@code by} 는 null 로 비운다). 부르는 서비스의 트랜잭션 안에서(대량 갱신 — 같은 트랜잭션에서 미리 읽은 신고 엔티티는 옛 값이다).
     */
    public long markCase(long caseId, String status, Instant at, Long by) {
        var update = query.update(REPORT).set(REPORT.status, status).where(REPORT.caseId.eq(caseId));
        update = at == null ? update.setNull(REPORT.resolvedAt) : update.set(REPORT.resolvedAt, at);
        update = by == null ? update.setNull(REPORT.resolvedBy) : update.set(REPORT.resolvedBy, by);
        return update.execute();
    }
}
