package com.community.ad.repository.master;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 광고 성과 읽기(ad-performance-contract 규칙 1·4) — 원천은 공개 노출 API 가 쌓는 일별 집계 {@code ad_daily_stats}(건·버전·KST 날짜·자리, V118)와 신청 건 처리 기록
 * {@code biz_application_events}(멈춘 구간). 쓰지 않는다. 날짜 범위는 끝 포함이고 PK 맨 앞이 신청 건이라 한 건·몇 건 읽기는 PK 로 탄다.
 */
@Repository
public class AdStatsStore {

    /** 집계 한 줄(버전·날짜·자리) */
    public record StatRow(long applicationId, int version, LocalDate day, String placement, long impressions, long clicks) {
    }

    /** 멈춘 구간을 짝지을 처리 기록 한 줄(오래된 차례) */
    public record EventRow(Instant at, String action, String note) {
    }

    /** 멈춘 구간의 시작·끝이 되는 처리 기록 코드 */
    static final List<String> DOWNTIME_ACTIONS = List.of("TAKEN_DOWN", "PAUSED", "STOPPED", "RESTORED", "RESUMED", "CANCELED", "FORFEITED");

    private final NamedParameterJdbcTemplate named;

    public AdStatsStore(DataSource dataSource) {
        this.named = new NamedParameterJdbcTemplate(dataSource);
    }

    /** 그 건들의 집계 줄 — 날짜 {@code [from, to]}(끝 포함) */
    public List<StatRow> rows(Collection<Long> applicationIds, LocalDate from, LocalDate to) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        return named.query("SELECT application_id, version, day, placement, impressions, clicks FROM ad_daily_stats "
                        + "WHERE application_id IN (:ids) AND day BETWEEN :from AND :to ORDER BY application_id, day, version, placement",
                new MapSqlParameterSource("ids", applicationIds).addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to)),
                (rs, i) -> new StatRow(rs.getLong(1), rs.getInt(2), rs.getDate(3).toLocalDate(), rs.getString(4), rs.getLong(5), rs.getLong(6)));
    }

    /** 그 건의 내림·정지·중단과 되돌리기·다시 켜기·취소 기록(오래된 차례) */
    public List<EventRow> downtimeEvents(long applicationId) {
        return named.query("SELECT created_at, action, note FROM biz_application_events WHERE application_id = :id AND action IN (:actions) ORDER BY created_at, id",
                new MapSqlParameterSource("id", applicationId).addValue("actions", DOWNTIME_ACTIONS),
                (rs, i) -> new EventRow(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3)));
    }
}
