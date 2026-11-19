package com.community.highlight.repository.master;

import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.entity.QHighlightEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 공개 「아추!」 페이지({@code GET /api/highlights})의 하이라이트 질의 — 한 곳에서 기간 안에 고른 것(achu-page-contract 「기간 정책」).
 * 고른 시각 = {@code coalesce(show_from, created_at)}({@link HighlightEntity#pickedAt}). 하이라이트 표는 운영자가 고른 줄만 있어 작다 — 곳 하나·기간 하나의 줄을 모두 읽고
 * 글 보임 판정(다른 표·게시판 사본)은 부르는 쪽이 한다.
 */
@Repository
public class HighlightPeriodRepositoryQDSL extends IrobitRepositoryQDSL {

    private static final QHighlightEntity HIGHLIGHT = QHighlightEntity.highlightEntity;

    public HighlightPeriodRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 고른 시각 식 — {@code coalesce(show_from, created_at)} */
    private static DateTimeExpression<Instant> pickedAt() {
        return Expressions.dateTimeTemplate(Instant.class, "coalesce({0}, {1})", HIGHLIGHT.showFrom, HIGHLIGHT.createdAt);
    }

    /**
     * 그 곳의 노출을 켠 하이라이트 중 고른 시각이 {@code [from, to)} 이고 {@code now} 이하인 것 — 노출 기간({@code show_until})이 끝난 것도 든다.
     * 차례는 고른 시각 내림차순, 같으면 id 내림차순.
     */
    public List<HighlightEntity> pickedBetween(String place, Instant from, Instant to, Instant now) {
        DateTimeExpression<Instant> picked = pickedAt();
        return query.selectFrom(HIGHLIGHT)
                .where(HIGHLIGHT.place.eq(place), HIGHLIGHT.visible.isTrue(), picked.goe(from), picked.lt(to), picked.loe(now))
                .orderBy(picked.desc(), HIGHLIGHT.id.desc())
                .fetch();
    }
}
