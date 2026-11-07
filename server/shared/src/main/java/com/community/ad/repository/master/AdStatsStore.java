package com.community.ad.repository.master;

import com.community.ad.entity.QAdDailyStatEntity;
import com.community.bizapplication.entity.QBizApplicationEventEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 광고 성과 읽기(ad-performance-contract 규칙 1·4) — 원천은 공개 노출 API 가 쌓는 일별 집계 {@code ad_daily_stats}(건·버전·KST 날짜·자리, V118)와 신청 건 처리 기록
 * {@code biz_application_events}(멈춘 구간). 쓰지 않는다. 날짜 범위는 끝 포함이고 PK 맨 앞이 신청 건이라 한 건·몇 건 읽기는 PK 로 탄다. QueryDSL 만 쓴다.
 */
@Repository
public class AdStatsStore extends RoboyamRepositoryQDSL {

    private static final QAdDailyStatEntity S = QAdDailyStatEntity.adDailyStatEntity;
    private static final QBizApplicationEventEntity E = QBizApplicationEventEntity.bizApplicationEventEntity;

    /** 집계 한 줄(버전·날짜·자리) */
    public record StatRow(long applicationId, int version, LocalDate day, String placement, long impressions, long clicks) {
    }

    /** 멈춘 구간을 짝지을 처리 기록 한 줄(오래된 차례) */
    public record EventRow(Instant at, String action, String note) {
    }

    /** 멈춘 구간의 시작·끝이 되는 처리 기록 코드 */
    static final List<String> DOWNTIME_ACTIONS = List.of("TAKEN_DOWN", "PAUSED", "STOPPED", "RESTORED", "RESUMED", "CANCELED", "FORFEITED");

    public AdStatsStore(JPAQueryFactory query) {
        super(query);
    }

    /** 그 건들의 집계 줄 — 날짜 {@code [from, to]}(끝 포함) */
    public List<StatRow> rows(Collection<Long> applicationIds, LocalDate from, LocalDate to) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        return query.select(Projections.constructor(StatRow.class, S.applicationId, S.version, S.day, S.placement, S.impressions, S.clicks)).from(S)
                .where(S.applicationId.in(applicationIds), S.day.between(from, to))
                .orderBy(S.applicationId.asc(), S.day.asc(), S.version.asc(), S.placement.asc()).fetch();
    }

    /** 그 건의 내림·정지·중단과 되돌리기·다시 켜기·취소 기록(오래된 차례) */
    public List<EventRow> downtimeEvents(long applicationId) {
        return query.select(Projections.constructor(EventRow.class, E.createdAt, E.action, E.note)).from(E)
                .where(E.applicationId.eq(applicationId), E.action.in(DOWNTIME_ACTIONS)).orderBy(E.createdAt.asc(), E.id.asc()).fetch();
    }
}
