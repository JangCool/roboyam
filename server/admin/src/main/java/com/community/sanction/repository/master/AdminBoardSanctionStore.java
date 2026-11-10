package com.community.sanction.repository.master;

import static com.community.sanction.repository.master.BoardSanctionStore.B;
import static com.community.sanction.repository.master.BoardSanctionStore.P;
import static com.community.sanction.repository.master.BoardSanctionStore.R;
import static com.community.sanction.repository.master.BoardSanctionStore.S;
import static com.community.sanction.repository.master.BoardSanctionStore.countWhen;
import static com.community.sanction.repository.master.BoardSanctionStore.running;
import static com.community.sanction.repository.master.BoardSanctionStore.zero;

import com.community.sanction.entity.BoardSanctionKind;
import com.community.sanction.entity.BoardSanctionReviewEntity;
import com.community.sanction.entity.QBoardSanctionEntity;
import com.community.sanction.entity.SanctionStatus;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.sanction.repository.master.BoardSanctionStore.ReviewRow;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 관리자 게시판 제재·반복 검토 목록·건수·회원 요약(읽기만) — 한 줄 모양은 shared {@link BoardSanctionStore}({@code sanctionRows}·{@code row}·{@code reviewRows}·{@code review}).
 * 색인: 목록은 V137 의 {@code ix_board_sanctions_created}·{@code ix_board_sanctions_running_list}·{@code ix_board_sanctions_closed_list}·{@code ix_board_sanctions_board},
 * 반복 검토는 {@code ix_board_sanction_reviews_open_list}·{@code ix_board_sanction_reviews_closed_list}, 회원 범위는 {@code ix_board_sanctions_user}.
 */
@Repository
public class AdminBoardSanctionStore extends RoboyamRepositoryQDSL {

    /** 하위 질의용 별칭 */
    private static final QBoardSanctionEntity X = new QBoardSanctionEntity("x");

    /** 목록 거르기 */
    public record Filter(String scope, Long boardId, BoardSanctionKind kind, String nicknameLike, Long sanctionNo, Instant from, Instant to, Long userId, boolean oldest) {
    }

    /** 게시판 거르기 고르개 한 줄 */
    public record BoardRef(long id, String slug, String name, String nameEn) {
    }

    /** 반복 검토 줄 + 지금(열린 건) 또는 닫은 때(닫힌 건)의 7일 건수 · 게시판 수 */
    public record ReviewLine(ReviewRow row, int recentCount, int recentBoardCount) {
    }

    /** 회원 상세의 게시판 하나 — 유효한 경고 수 · 유효한 쓰기 제한 수 · 진행 중 쓰기 제한의 끝 */
    public record MemberBoardRow(BoardRef board, long warnings, long restrictions, Instant activeUntil) {
    }

    private final BoardSanctionStore store;

    public AdminBoardSanctionStore(JPAQueryFactory query, BoardSanctionStore store) {
        super(query);
        this.store = store;
    }

    /** 범위 「끝남」 — 기간 끝·해제·무효 */
    private static BooleanExpression closed(QBoardSanctionEntity s) {
        return s.status.ne(SanctionStatus.ACTIVE);
    }

    // ── 제재 목록 ─────────────────────────────────────────────────────────

    /** 전체 건수(거르기와 무관) — all · active · closed. 예전과 같이 한 문장(세 칸이 같은 시점 — 문장 사이 커밋이 숫자를 어긋나게 하지 않는다) */
    public long[] counts(Instant now) {
        NumberExpression<Long> active = countWhen(running(S, now));
        NumberExpression<Long> ended = countWhen(closed(S));
        Tuple t = query.select(S.count(), active, ended).from(S).fetchOne();
        return t == null ? new long[3] : new long[]{zero(t.get(S.count())), zero(t.get(active)), zero(t.get(ended))};
    }

    public long total(Filter filter, Instant now) {
        JPAQuery<Long> count = query.select(S.count()).from(S);
        if (filter.nicknameLike() != null) {
            count = count.leftJoin(P).on(P.userId.eq(S.userId));
        }
        return zero(count.where(where(filter, now)).fetchOne());
    }

    public List<BoardSanctionRow> page(Filter filter, Instant now, int page, int size) {
        OrderSpecifier<?>[] order = filter.oldest() ? new OrderSpecifier<?>[]{S.createdAt.asc(), S.id.asc()} : new OrderSpecifier<?>[]{S.createdAt.desc(), S.id.desc()};
        return store.sanctionRows().where(where(filter, now)).orderBy(order).offset((long) page * size).limit(size).fetch().stream().map(BoardSanctionStore::row)
                .toList();
    }

    private static BooleanExpression where(Filter f, Instant now) {
        List<BooleanExpression> parts = new ArrayList<>();
        if ("ACTIVE".equals(f.scope())) {
            parts.add(running(S, now));
        } else if ("CLOSED".equals(f.scope())) {
            parts.add(closed(S));
        }
        if (f.boardId() != null) {
            parts.add(S.boardId.eq(f.boardId()));
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
        }
        return parts.stream().reduce(BooleanExpression::and).orElse(null);
    }

    /** 게시판 제재가 한 건이라도 있는 게시판(이름 차례) — 게시판 거르기 고르개 */
    public List<BoardRef> boards() {
        return query.select(B.id, B.slug, B.name, B.nameEn).from(B).where(JPAExpressions.selectOne().from(X).where(X.boardId.eq(B.id)).exists())
                .orderBy(B.name.asc(), B.id.asc()).fetch().stream().map(t -> new BoardRef(t.get(B.id), t.get(B.slug), t.get(B.name), t.get(B.nameEn))).toList();
    }

    // ── 회원 상세 ─────────────────────────────────────────────────────────

    /** 그 회원의 게시판별 수 — 게시판 이름 차례 */
    public List<MemberBoardRow> memberBoards(long userId, Instant now) {
        BooleanExpression valid = S.status.ne(SanctionStatus.VOIDED);
        NumberExpression<Long> warnings = countWhen(valid.and(S.kind.eq(BoardSanctionKind.WARNING)));
        NumberExpression<Long> restrictions = countWhen(valid.and(S.kind.ne(BoardSanctionKind.WARNING)));
        var until = BoardSanctionStore.runningUntilMax(S, now);
        return query.select(S.boardId, B.slug, B.name, B.nameEn, warnings, restrictions, until).from(S).join(B).on(B.id.eq(S.boardId)).where(S.userId.eq(userId))
                .groupBy(S.boardId, B.slug, B.name, B.nameEn).orderBy(B.name.asc(), S.boardId.asc()).fetch().stream()
                .map(t -> new MemberBoardRow(new BoardRef(t.get(S.boardId), t.get(B.slug), t.get(B.name), t.get(B.nameEn)), zero(t.get(warnings)),
                        zero(t.get(restrictions)), t.get(until))).toList();
    }

    // ── 반복 검토 ─────────────────────────────────────────────────────────

    /** 열린 건 · 닫힌 건 수 */
    public long[] reviewCounts() {
        NumberExpression<Long> open = countWhen(R.status.eq(BoardSanctionReviewEntity.OPEN));
        NumberExpression<Long> closed = countWhen(R.status.eq(BoardSanctionReviewEntity.CLOSED));
        Tuple t = query.select(open, closed).from(R).fetchOne();
        return new long[]{zero(t == null ? null : t.get(open)), zero(t == null ? null : t.get(closed))};
    }

    /** 열린 건 수(탭·메뉴 배지) — 부분 색인 */
    public long openReviews() {
        return zero(query.select(R.count()).from(R).where(R.status.eq(BoardSanctionReviewEntity.OPEN)).fetchOne());
    }

    /** 한 쪽 — 열린 건 오래된 것이 위, 닫힌 건 최근 닫은 것이 위. 건수는 줄마다 회원·게시판 범위 색인 COUNT */
    public List<ReviewLine> reviewPage(boolean open, Instant now, int page, int size) {
        Instant from = now.minus(BoardSanctionStore.REVIEW_WINDOW);
        OrderSpecifier<?>[] order = open ? new OrderSpecifier<?>[]{R.openedAt.asc(), R.id.asc()} : new OrderSpecifier<?>[]{R.closedAt.desc(), R.id.desc()};
        List<ReviewRow> rows = store.reviewRows().where(R.status.eq(open ? BoardSanctionReviewEntity.OPEN : BoardSanctionReviewEntity.CLOSED)).orderBy(order)
                .offset((long) page * size).limit(size).fetch().stream().map(BoardSanctionStore::review).toList();
        List<ReviewLine> out = new ArrayList<>(rows.size());
        for (ReviewRow r : rows) {
            out.add(line(r, from));
        }
        return out;
    }

    /** 검토 한 줄의 건수 — 열린 건은 지금 기준(7일 · VOIDED 제외 · 마지막 닫힌 검토 이후), 닫힌 건은 닫은 때 저장한 수 */
    public ReviewLine line(ReviewRow r, Instant windowFrom) {
        if (!r.open()) {
            return new ReviewLine(r, r.closedCount() == null ? r.openedCount() : r.closedCount(), r.closedBoardCount() == null ? 1 : r.closedBoardCount());
        }
        Instant from = r.countedAfter() != null && r.countedAfter().isAfter(windowFrom) ? r.countedAfter() : windowFrom;
        return new ReviewLine(r, store.countValidAfter(r.userId(), r.boardId(), from), store.boardsValidAfter(r.userId(), windowFrom));
    }
}
