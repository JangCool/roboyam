package com.community.point.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.point.dto.PointLedgerFilter;
import com.community.point.dto.PointLedgerRow;
import com.community.point.dto.PointMemberRow;
import com.community.point.entity.QPointBalanceEntity;
import com.community.point.entity.QPointLedgerEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 관리자 포인트 원장 조회(조건·쪽·같은 조건의 합계)와 조정 대상 회원 찾기·회원 상세의 최근 내역. 정렬·집계·쪽 나누기는 DB 에서 한다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p><b>탈퇴한 회원</b>의 원장은 보이지 않는다(포인트는 탈퇴로 소멸한다 — 화면 계약). 회원 검색어는 닉네임(부분, 대소문자 무시) · 회원번호(같음) · 이메일 —
 * {@code member.pii} 가 있으면 부분 일치, 없으면 <b>전체 주소가 같을 때만</b>(가려진 값으로 짐작해 찾지 못하게, 회원 목록과 같은 규칙).
 */
@Repository
public class PointLedgerRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QPointLedgerEntity LEDGER = QPointLedgerEntity.pointLedgerEntity;
    private static final QPointBalanceEntity BALANCE = QPointBalanceEntity.pointBalanceEntity;
    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;

    private static final BooleanExpression NEVER = Expressions.booleanTemplate("1 = 0");

    public PointLedgerRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 조건에 맞는 원장 한 쪽 — 최신이 위 */
    public List<PointLedgerRow> page(PointLedgerFilter filter, boolean canPii, int page, int size) {
        return base().select(Projections.constructor(PointLedgerRow.class, LEDGER.id, LEDGER.userId, PROFILE.nickname, LEDGER.kind, LEDGER.reason, LEDGER.note,
                        LEDGER.delta, LEDGER.freeDelta, LEDGER.paidDelta, LEDGER.balanceAfter, OPERATOR.operatorName, LEDGER.createdAt))
                .leftJoin(OPERATOR).on(OPERATOR.id.eq(LEDGER.actorOperatorId))
                .where(where(filter, canPii))
                .orderBy(LEDGER.createdAt.desc(), LEDGER.id.desc())
                .offset((long) page * size).limit(size)
                .fetch();
    }

    /** 같은 조건의 전체 줄 수와 합계 — [줄 수, 지급 합, 차감·회수·사용 합(양수), 운영 조정 건수] */
    public long[] summary(PointLedgerFilter filter, boolean canPii) {
        NumberExpression<Long> earned = new CaseBuilder().when(LEDGER.delta.gt(0)).then(LEDGER.delta.longValue()).otherwise(0L).sum();
        NumberExpression<Long> spent = new CaseBuilder().when(LEDGER.delta.lt(0)).then(LEDGER.delta.longValue().negate()).otherwise(0L).sum();
        NumberExpression<Long> adjusts = new CaseBuilder().when(LEDGER.reason.eq("ADMIN_ADJUST")).then(1L).otherwise(0L).sum();
        Tuple row = base().select(LEDGER.count(), earned, spent, adjusts).where(where(filter, canPii)).fetchOne();
        if (row == null) {
            return new long[] {0, 0, 0, 0};
        }
        return new long[] {nz(row.get(0, Long.class)), nz(row.get(1, Long.class)), nz(row.get(2, Long.class)), nz(row.get(3, Long.class))};
    }

    /** 한 회원의 최근 원장 {@code limit} 줄(회원 상세) — 최신이 위 */
    public List<PointLedgerRow> recentOf(long userId, int limit) {
        return query.select(Projections.constructor(PointLedgerRow.class, LEDGER.id, LEDGER.userId, Expressions.nullExpression(String.class), LEDGER.kind,
                        LEDGER.reason, LEDGER.note, LEDGER.delta, LEDGER.freeDelta, LEDGER.paidDelta, LEDGER.balanceAfter, Expressions.nullExpression(String.class),
                        LEDGER.createdAt))
                .from(LEDGER).where(LEDGER.userId.eq(userId)).orderBy(LEDGER.id.desc()).limit(limit).fetch();
    }

    /** 조정 대상 후보 — 탈퇴하지 않은 회원(정상·정지), 최근 가입이 위, 최대 {@code limit} 명. 잔액(합계·무상·유상) — 잔액 행이 없으면 0P */
    public List<PointMemberRow> candidates(String q, boolean canPii, int limit) {
        BooleanBuilder where = new BooleanBuilder(USER.status.ne(UserStatus.WITHDRAWN)).and(USER.deletedAt.isNull());
        if (q != null && !q.isBlank()) {
            where.and(keyword(q.strip().toLowerCase(), canPii));
        }
        return query.select(Projections.constructor(PointMemberRow.class, USER.id, PROFILE.nickname, USER.email, BALANCE.balance.coalesce(0L),
                        BALANCE.freeBalance.coalesce(0L), BALANCE.paidBalance.coalesce(0L)))
                .from(USER).join(PROFILE).on(PROFILE.userId.eq(USER.id)).leftJoin(BALANCE).on(BALANCE.userId.eq(USER.id))
                .where(where).orderBy(USER.id.desc()).limit(limit).fetch();
    }

    private JPAQuery<?> base() {
        return query.from(LEDGER).join(USER).on(USER.id.eq(LEDGER.userId)).join(PROFILE).on(PROFILE.userId.eq(LEDGER.userId));
    }

    private static BooleanBuilder where(PointLedgerFilter f, boolean canPii) {
        BooleanBuilder where = new BooleanBuilder(USER.status.ne(UserStatus.WITHDRAWN)).and(USER.deletedAt.isNull());
        if (f.from() != null) {
            where.and(LEDGER.createdAt.goe(f.from()));
        }
        if (f.to() != null) {
            where.and(LEDGER.createdAt.loe(f.to()));
        }
        if (f.kind() != null) {
            where.and(LEDGER.kind.eq(f.kind()));
        }
        if (f.reasons() != null && !f.reasons().isEmpty()) {
            where.and(LEDGER.reason.in(f.reasons()));
        }
        if (f.q() != null) {
            where.and(keyword(f.q(), canPii));
        }
        return where;
    }

    /** 회원 검색어 — 닉네임(부분) · 회원번호(같음) · 이메일(member.pii 가 있으면 부분, 없으면 전체 주소가 같을 때만). {@code q} 는 소문자로 다듬은 값 */
    private static BooleanExpression keyword(String q, boolean canPii) {
        BooleanExpression byNickname = PROFILE.nickname.lower().contains(q);
        BooleanExpression byEmail = canPii ? USER.email.contains(q) : USER.email.eq(q);
        BooleanExpression byNumber = NEVER;
        if (!q.isEmpty() && q.length() <= 19 && q.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            try {
                byNumber = USER.id.eq(Long.parseLong(q));
            } catch (NumberFormatException e) {
                byNumber = NEVER;
            }
        }
        return byNumber.or(byNickname).or(byEmail);
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
