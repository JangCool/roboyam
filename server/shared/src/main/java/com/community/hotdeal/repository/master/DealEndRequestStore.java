package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.DealEndRequestEntity;
import com.community.hotdeal.entity.QDealEndRequestEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 종료 요청 줄({@code deal_end_requests}, V122) — 글·회원마다 한 줄. <b>부르는 쪽 트랜잭션에 참여한다.</b> 요청자 신원은 이 표 밖으로 내지 않는다.
 * 읽기는 프로젝션(의심 구간 켜기·끄기는 대량 UPDATE).
 *
 * <p><b>의심 구간</b>({@code flagged_period}): 지금 회차(마지막 「아직 진행 중」 뒤 — 운영진 재개는 줄을 지운다)의 요청이 문턱에 닿아 의심이 되면 그 회차의 줄을 모두 켜고,
 * 의심인 동안 들어온 요청도 켠 채로 넣는다. 「아직 진행 중」은 모두 끈다(작성자가 반박한 회차). 보상(규칙 5)은 딜이 끝날 때 켜진 줄의 요청자에게만 간다.
 */
@Repository
public class DealEndRequestStore extends RoboyamRepositoryQDSL {

    private static final QDealEndRequestEntity E = QDealEndRequestEntity.dealEndRequestEntity;

    private final DealEndRequestRepository requests;

    public DealEndRequestStore(JPAQueryFactory query, DealEndRequestRepository requests) {
        super(query);
        this.requests = requests;
    }

    /** 보상 대상 한 줄 — 요청 ID(원장 대상) · 요청한 회원 */
    public record Requester(long requestId, long memberId) {
    }

    public boolean exists(long postId, long memberId) {
        return query.selectOne().from(E).where(E.postId.eq(postId), E.memberId.eq(memberId)).fetchFirst() != null;
    }

    public void insert(long id, long postId, long memberId, Long authorId, String reason, boolean flaggedPeriod, boolean fromReport, Instant now) {
        requests.persist(DealEndRequestEntity.create(id, postId, memberId, authorId, reason, flaggedPeriod, fromReport, now));
    }

    /** 그 회원이 {@code since} 뒤에 보낸 요청 수(하루 한도 — 한국 0시부터) */
    public int countByMemberSince(long memberId, Instant since) {
        return count(E.memberId.eq(memberId).and(E.createdAt.goe(since)));
    }

    /** 그 회원이 {@code since} 뒤에 같은 작성자의 글에 보낸 요청 수(같은 작성자 기간 한도) */
    public int countByMemberAndAuthorSince(long memberId, long authorId, Instant since) {
        return count(E.memberId.eq(memberId).and(E.authorId.eq(authorId)).and(E.createdAt.goe(since)));
    }

    /** 지금 회차의 요청 수 — {@code keptAt}(마지막 「아직 진행 중」) 뒤의 줄, 없으면 모든 줄 */
    public int countRound(long postId, Instant keptAt) {
        return count(round(postId, keptAt));
    }

    /** 지금 회차의 줄을 의심 구간으로 */
    public void markRoundFlagged(long postId, Instant keptAt) {
        query.update(E).set(E.flaggedPeriod, true).where(round(postId, keptAt)).execute();
    }

    /** 「아직 진행 중」 — 그 글의 의심 구간 표시를 모두 끈다 */
    public void clearFlagged(long postId) {
        query.update(E).set(E.flaggedPeriod, false).where(E.postId.eq(postId), E.flaggedPeriod.isTrue()).execute();
    }

    /** 의심 구간 요청자(보상 대상) — 요청 차례 */
    public List<Requester> flaggedRequesters(long postId) {
        return query.select(Projections.constructor(Requester.class, E.id, E.memberId)).from(E).where(E.postId.eq(postId), E.flaggedPeriod.isTrue())
                .orderBy(E.createdAt.asc(), E.id.asc()).fetch();
    }

    /** 운영진 재개 — 그 글의 요청 줄을 모두 지운다(의심·요청 리셋) */
    public int deleteForPost(long postId) {
        return (int) query.delete(E).where(E.postId.eq(postId)).execute();
    }

    /** 탈퇴 — 그 회원의 요청 줄을 지운다(이미 의심이 된 딜의 상태는 그대로 — 규칙 6) */
    public int deleteForMember(long memberId) {
        return (int) query.delete(E).where(E.memberId.eq(memberId)).execute();
    }

    private static BooleanExpression round(long postId, Instant keptAt) {
        return keptAt == null ? E.postId.eq(postId) : E.postId.eq(postId).and(E.createdAt.gt(keptAt));
    }

    private int count(BooleanExpression where) {
        Long n = query.select(E.count()).from(E).where(where).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }
}
