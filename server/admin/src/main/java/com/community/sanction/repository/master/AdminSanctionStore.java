package com.community.sanction.repository.master;

import static com.community.sanction.repository.master.MemberSanctionStore.A;
import static com.community.sanction.repository.master.MemberSanctionStore.P;
import static com.community.sanction.repository.master.MemberSanctionStore.S;

import com.community.sanction.entity.AppealStatus;
import com.community.sanction.entity.BoardSanctionKind;
import com.community.sanction.entity.QBoardSanctionEntity;
import com.community.sanction.entity.QMemberSanctionEntity;
import com.community.sanction.entity.SanctionKind;
import com.community.sanction.entity.SanctionStatus;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 관리자 제재·이의제기 목록·건수·회원 찾기·대시보드 셈(읽기만) — 한 줄 모양은 shared {@link MemberSanctionStore}({@code sanctionRows}·{@code row}·{@code appealRows}·{@code appeal}).
 * 색인: 목록·건수는 V136 의 부분 색인({@code ix_member_sanctions_running_list} · {@code ix_member_sanctions_closed_list} · {@code ix_sanction_appeals_pending} ·
 * {@code ix_sanction_appeals_status}), 회원 찾기는 닉네임 trigram 색인({@code ix_profiles_nickname_trgm}).
 */
@Repository
public class AdminSanctionStore extends RoboyamRepositoryQDSL {

    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QMemberSanctionEntity X = new QMemberSanctionEntity("x");
    private static final QMemberSanctionEntity SITE = new QMemberSanctionEntity("siteSanction");
    private static final QBoardSanctionEntity BOARD = new QBoardSanctionEntity("boardSanction");
    private static final QProfileEntity APPELLANT = new QProfileEntity("appellantProfile");

    /** 목록 거르기 */
    public record Filter(String scope, SanctionKind kind, String nicknameLike, Long sanctionNo, Instant from, Instant to, Long userId, boolean oldest) {
    }

    /** 회원 찾기 한 줄 */
    public record TargetRow(long id, String nickname, String gradeKey, String status, int warningCount) {
    }

    /** 대시보드의 오래된 대기 이의제기 한 줄 */
    /** @param kind 사이트 제재 단계, 게시판 제재면 {@code BOARD_} + 단계(번들 {@code member.sanction.kind.BOARD_*}) */
    public record PendingAppealRow(long id, String nickname, String kind, Integer warningNo, Instant appealedAt, boolean sealed) {
    }

    private final MemberSanctionStore store;

    public AdminSanctionStore(JPAQueryFactory query, MemberSanctionStore store) {
        super(query);
        this.store = store;
    }

    /** 범위 「진행 중」 — 진행 중인 정지·영구 정지(끝이 지난 것은 뺀다, 크론이 1분 안에 끝낸다) */
    private static BooleanExpression runningAt(Instant now) {
        return MemberSanctionStore.runningStored(S).and(S.endsAt.isNull().or(S.endsAt.gt(now)));
    }

    /** 범위 「끝남」 */
    private static BooleanExpression closed() {
        return S.status.ne(SanctionStatus.ACTIVE);
    }

    // ── 제재 목록 ─────────────────────────────────────────────────────────

    /** 전체 건수(거르기와 무관) — all · active · closed. 예전과 같이 한 문장(세 칸이 같은 시점 — 문장 사이 커밋이 숫자를 어긋나게 하지 않는다) */
    public long[] counts(Instant now) {
        NumberExpression<Long> running = BoardSanctionStore.countWhen(runningAt(now));
        NumberExpression<Long> ended = BoardSanctionStore.countWhen(closed());
        Tuple t = query.select(S.count(), running, ended).from(S).fetchOne();
        return t == null ? new long[3] : new long[]{nz(t.get(S.count())), nz(t.get(running)), nz(t.get(ended))};
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    public long total(Filter filter, Instant now) {
        JPAQuery<Long> count = query.select(S.count()).from(S);
        if (filter.nicknameLike() != null) {
            count = count.leftJoin(P).on(P.userId.eq(S.userId));
        }
        return count(count.where(where(filter, now)));
    }

    public List<SanctionRow> page(Filter filter, Instant now, int page, int size) {
        OrderSpecifier<?>[] order = filter.oldest() ? new OrderSpecifier<?>[]{S.createdAt.asc(), S.id.asc()} : new OrderSpecifier<?>[]{S.createdAt.desc(), S.id.desc()};
        return store.sanctionRows().where(where(filter, now)).orderBy(order).offset((long) page * size).limit(size).fetch().stream().map(MemberSanctionStore::row)
                .toList();
    }

    private static BooleanExpression where(Filter f, Instant now) {
        List<BooleanExpression> parts = new ArrayList<>();
        if ("ACTIVE".equals(f.scope())) {
            parts.add(runningAt(now));
        } else if ("CLOSED".equals(f.scope())) {
            parts.add(closed());
        }
        if (f.kind() != null) {
            parts.add(S.kind.eq(f.kind()));
        }
        if (f.nicknameLike() != null) {
            parts.add(P.nickname.lower().like(f.nicknameLike(), '\\'));
            parts.add(S.sealed.isFalse()); // 봉인 제재(V184)는 닉네임으로 찾지 않는다
        }
        if (f.sanctionNo() != null) {
            parts.add(S.sanctionNo.eq(f.sanctionNo()));
        }
        if (f.from() != null) {
            parts.add(S.createdAt.goe(f.from()));
        }
        if (f.to() != null) {
            parts.add(S.createdAt.loe(f.to()));
        }
        if (f.userId() != null) {
            parts.add(S.userId.eq(f.userId()));
            parts.add(S.sealed.isFalse()); // 회원으로 거른 목록(회원 상세 · 「이 회원 제재 보기」)에는 봉인 제재(V184)를 싣지 않는다 — anonymous-board-contract 2026-11-11
        }
        return parts.stream().reduce(BooleanExpression::and).orElse(null);
    }

    /** 제재 여럿(ID 로) — 이의제기 목록의 제재 요약 */
    public Map<Long, SanctionRow> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return store.sanctionRows().where(S.id.in(ids)).fetch().stream().map(MemberSanctionStore::row).collect(Collectors.toMap(SanctionRow::id, r -> r));
    }

    // ── 회원 찾기 ─────────────────────────────────────────────────────────

    /** 닉네임에 {@code like} 가 들어간 회원(탈퇴·탈퇴 진행 중 제외) 최대 {@code limit} — 같은 닉네임 · 짧은 닉네임 먼저 */
    public List<TargetRow> targets(String like, String exact, int limit) {
        var warnings = JPAExpressions.select(X.count()).from(X)
                .where(X.userId.eq(U.id), X.kind.eq(SanctionKind.WARNING), X.status.eq(SanctionStatus.ACTIVE));
        return query.select(U.id, P.nickname, P.gradeKey, U.status, warnings).from(P).join(U).on(U.id.eq(P.userId))
                .where(P.nickname.lower().like(like, '\\'), U.deletedAt.isNull(), U.status.notIn(UserStatus.WITHDRAWN, UserStatus.WITHDRAWING))
                .orderBy(new CaseBuilder().when(P.nickname.lower().eq(exact)).then(1).otherwise(0).desc(), P.nickname.length().asc(), P.nickname.asc(), U.id.asc())
                .limit(limit).fetch().stream()
                .map(t -> {
                    Long n = t.get(warnings);
                    UserStatus status = t.get(U.status);
                    return new TargetRow(t.get(U.id), t.get(P.nickname), t.get(P.gradeKey), status == null ? null : status.name(), n == null ? 0 : n.intValue());
                }).toList();
    }

    // ── 이의제기 ──────────────────────────────────────────────────────────

    /** 상태별 전체 건수 — pending · accepted · rejected */
    public long[] appealCounts() {
        long[] out = new long[3];
        var count = A.count();
        for (Tuple t : query.select(A.status, count).from(A).groupBy(A.status).fetch()) {
            AppealStatus status = t.get(A.status);
            Long n = t.get(count);
            out[status.ordinal()] = n == null ? 0 : n;
        }
        return out;
    }

    public long pendingAppeals() {
        return count(query.select(A.count()).from(A).where(A.status.eq(AppealStatus.PENDING)));
    }

    public long appealTotal(AppealStatus status) {
        return count(query.select(A.count()).from(A).where(status == null ? null : A.status.eq(status)));
    }

    public List<AppealRow> appealPage(AppealStatus status, boolean newest, int page, int size) {
        OrderSpecifier<?>[] order = newest ? new OrderSpecifier<?>[]{A.createdAt.desc(), A.id.desc()} : new OrderSpecifier<?>[]{A.createdAt.asc(), A.id.asc()};
        return store.appealRows().where(status == null ? null : A.status.eq(status)).orderBy(order).offset((long) page * size).limit(size).fetch().stream()
                .map(MemberSanctionStore::appeal).toList();
    }

    /** 대기 이의제기 오래된 차례(대시보드) — 색인 {@code ix_sanction_appeals_pending}. 게시판 제재 이의제기(V137)도 — 사이트 제재 · 게시판 제재 중 하나만 있다 */
    public List<PendingAppealRow> oldestPending(int limit) {
        return query.select(A.id, APPELLANT.nickname, SITE.kind, BOARD.kind, SITE.warningNo, A.createdAt, SITE.sealed, BOARD.sealed).from(A)
                .leftJoin(SITE).on(SITE.id.eq(A.sanctionId)).leftJoin(BOARD).on(BOARD.id.eq(A.boardSanctionId))
                .leftJoin(APPELLANT).on(APPELLANT.userId.eq(A.userId))
                .where(A.status.eq(AppealStatus.PENDING)).orderBy(A.createdAt.asc(), A.id.asc()).limit(limit).fetch().stream()
                .map(t -> {
                    SanctionKind site = t.get(SITE.kind);
                    BoardSanctionKind board = t.get(BOARD.kind);
                    String kind = site != null ? site.name() : board != null ? "BOARD_" + board.name() : null;
                    // 익명 글로 받은 봉인 제재의 이의제기는 닉네임을 싣지 않는다(anonymous-board-contract 정책 8·9)
                    boolean sealed = Boolean.TRUE.equals(t.get(SITE.sealed)) || Boolean.TRUE.equals(t.get(BOARD.sealed));
                    return new PendingAppealRow(t.get(A.id), sealed ? null : t.get(APPELLANT.nickname), kind, t.get(SITE.warningNo), t.get(A.createdAt), sealed);
                }).toList();
    }

    /** 진행 중인 정지·영구 정지 수(대시보드) — 부분 색인 */
    public long running(Instant now) {
        return count(query.select(S.count()).from(S).where(runningAt(now)));
    }

    private static long count(JPAQuery<Long> count) {
        Long n = count.fetchOne();
        return n == null ? 0 : n;
    }
}
