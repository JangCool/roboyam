package com.community.point.repository.master;

import com.community.point.entity.PointPopularRewardEntity;
import com.community.point.entity.QPointPopularRewardEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 인기글 보상 대상({@code point_popular_rewards}, V238) — 날짜마다 한 번 뽑아 남기고, 지급은 남긴 목록으로 잇는다. 트랜잭션은 부르는 배치가 연다.
 */
@Repository
public class PointPopularRewardStore extends IrobitRepositoryQDSL {

    private static final QPointPopularRewardEntity R = QPointPopularRewardEntity.pointPopularRewardEntity;

    private final EntityManager entityManager;

    public PointPopularRewardStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /** 그 날짜의 대상을 이미 뽑았나 */
    public boolean picked(LocalDate day) {
        return query.selectOne().from(R).where(R.rewardDay.eq(day)).fetchFirst() != null;
    }

    /**
     * 그 날짜 [{@code from}, {@code to}) 에 올라온 인기글 상위 {@code topN} 개를 뽑아 남긴다 — 인기글 「어제」 탭(R__batch_24 {@code batch_popular_rank} 의 YESTERDAY)과 같은 기준:
     * 후보·점수는 DB 함수 {@code popular_rank_candidates}(감쇠 없음 — 기준 시각 {@code now}, 같은 제외 규칙), 한 글쓴이는 설정 {@code popular_settings.max_per_author} 개까지,
     * 점수 높은 순(같으면 나중 글). 채우기(앞 기간 글)는 하지 않는다 — 그날 글만 보상한다. 이미 뽑은 날짜면 넣지 않는다({@code ON CONFLICT DO NOTHING}).
     *
     * <p>Native SQL 인 이유: DB 함수({@code popular_rank_candidates}) + 윈도 함수({@code row_number() OVER (PARTITION BY …)}) 집계(repository.md 인정 사유) — 인기글 배치와 같은 식을
     * 두 번 만들지 않으려고 그 함수를 그대로 부른다.
     *
     * @return 남긴 줄 수
     */
    public int pick(LocalDate day, Instant from, Instant to, Instant now, int topN) {
        return entityManager.createNativeQuery("insert into point_popular_rewards (day, rank, post_id, author_id, score, created_at) "
                        + "select ?1, row_number() over (order by k.score desc, k.post_id desc), k.post_id, p.author_id, k.score, ?4 "
                        + "from (select c.post_id, c.score from (select x.post_id, x.score, "
                        + "row_number() over (partition by x.author_key order by x.score desc, x.post_id desc) as author_rn "
                        + "from popular_rank_candidates(?2, ?3, ?4, false) x) c "
                        + "where c.author_rn <= (select coalesce(max(s.max_per_author), 2) from popular_settings s where s.id = 1) "
                        + "order by c.score desc, c.post_id desc limit ?5) k "
                        + "join posts p on p.id = k.post_id "
                        + "on conflict do nothing")
                .setParameter(1, day).setParameter(2, from).setParameter(3, to).setParameter(4, now).setParameter(5, topN).executeUpdate();
    }

    /** 뽑은 대상 하나(지급 덩어리의 가벼운 값) */
    public record Pick(int rank, long postId, Long authorId) {
    }

    /** 그 날짜에 뽑은 대상 중 순위가 {@code afterRank} 보다 큰 것을 순위 차례로 {@code limit} 개 */
    public List<Pick> picks(LocalDate day, int afterRank, int limit) {
        return query.select(com.querydsl.core.types.Projections.constructor(Pick.class, R.rank, R.postId, R.authorId)).from(R)
                .where(R.rewardDay.eq(day), R.rank.gt(afterRank)).orderBy(R.rank.asc()).limit(limit).fetch();
    }

    /** 「실패 건만 다시」 — 그 날짜의 그 순위들 */
    public List<Pick> picks(LocalDate day, java.util.Collection<Integer> ranks) {
        if (ranks.isEmpty()) {
            return List.of();
        }
        return query.select(com.querydsl.core.types.Projections.constructor(Pick.class, R.rank, R.postId, R.authorId)).from(R)
                .where(R.rewardDay.eq(day), R.rank.in(ranks)).orderBy(R.rank.asc()).fetch();
    }

    /** 그 날짜에 뽑은 대상 — 순위 차례 */
    public List<PointPopularRewardEntity> of(LocalDate day) {
        return query.selectFrom(R).where(R.rewardDay.eq(day)).orderBy(R.rank.asc()).fetch();
    }
}
