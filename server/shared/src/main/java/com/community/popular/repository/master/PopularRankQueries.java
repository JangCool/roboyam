package com.community.popular.repository.master;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 인기글 순위 스냅샷({@code post_rank_snapshots}) 읽기와 기간 범위 계산 — 공개 인기글(api)과 관리자 인기글 관리·설정(admin)이 같이 쓴다. <b>트랜잭션은 부르는 서비스가 연다.</b>
 *
 * <p>Native SQL 인 이유: ① 기간의 범위는 DB 배치({@code R__batch_24_popular_rank.sql})와 같은 SQL 함수 {@code popular_period_range} 로 계산해야 응답의
 * {@code from}·{@code to} 와 순위 계산이 어긋나지 않는다(한국시간 달력·설정의 주 시작 요일·DB 시각 {@code now()}). ② 스냅샷 표는 DB 배치만 쓰고 앱은 읽기만 해 엔티티를 두지 않는다.
 * 시각은 드라이버·Hibernate 의 시간대 변환을 거치지 않게 epoch 밀리초로 받는다.
 */
@Repository
public class PopularRankQueries {

    /** 기간의 범위 [from, to) */
    public record Range(Instant from, Instant to) {
    }

    /**
     * 순위 한 줄 — 순위 순서.
     *
     * @param rank       스냅샷의 순위(1부터)
     * @param score      계산 시점의 점수
     * @param fill       채운 줄의 출처(YESTERDAY · LAST_WEEK), 자기 기간 글이면 null
     * @param computedAt 이 스냅샷을 계산한 시각
     */
    public record Entry(int rank, long postId, double score, String fill, Instant computedAt) {
    }

    private final EntityManager entityManager;

    public PopularRankQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 지금(DB 시각) 그 기간의 범위. 모르는 기간이면 null */
    public Range range(String period) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        SELECT (extract(epoch FROM r.range_from) * 1000)::bigint, (extract(epoch FROM r.range_to) * 1000)::bigint
                          FROM popular_period_range(CAST(:period AS text), now()) r
                        """)
                .setParameter("period", period)
                .getSingleResult();
        if (row[0] == null || row[1] == null) {
            return null;
        }
        return new Range(Instant.ofEpochMilli(((Number) row[0]).longValue()), Instant.ofEpochMilli(((Number) row[1]).longValue()));
    }

    /** 그 기간의 순위 스냅샷(최대 50줄, 순위 순). 아직 계산되지 않았으면 빈 목록 */
    public List<Entry> snapshot(String period) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT rank, post_id, score, fill, (extract(epoch FROM computed_at) * 1000)::bigint
                          FROM post_rank_snapshots WHERE period = CAST(:period AS text) ORDER BY rank
                        """)
                .setParameter("period", period)
                .getResultList();
        return rows.stream().map(r -> new Entry(((Number) r[0]).intValue(), ((Number) r[1]).longValue(), ((Number) r[2]).doubleValue(), (String) r[3],
                Instant.ofEpochMilli(((Number) r[4]).longValue()))).toList();
    }
}
