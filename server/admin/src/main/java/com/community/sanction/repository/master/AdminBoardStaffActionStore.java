package com.community.sanction.repository.master;

import static com.community.sanction.repository.master.BoardStaffActionStore.ACTION;
import static com.community.sanction.repository.master.BoardStaffActionStore.ACTOR;

import com.community.board.entity.QBoardEntity;
import com.community.sanction.repository.master.AdminBoardSanctionStore.BoardRef;
import com.community.sanction.repository.master.BoardStaffActionStore.ActionRow;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 관리자 「스텝 처리 감독」 목록(읽기만, board-staff-action-contract 「API > 관리자」) — 한 줄 모양은 shared {@link BoardStaffActionStore}({@code actionRows}·{@code row}).
 * 제재 줄(kind SANCTION)은 넣지 않는다. 색인: V138 의 {@code ix_board_staff_actions_content}(전체) · {@code ix_board_staff_actions_board_content}(게시판).
 */
@Repository
public class AdminBoardStaffActionStore extends IrobitRepositoryQDSL {

    private static final QBoardEntity B = QBoardEntity.boardEntity;

    /**
     * 목록 거르기.
     *
     * @param kind         POST_HIDE · POST_DELETE · COMMENT_DELETE(null 이면 전체)
     * @param restored     OPEN(되돌리지 않음) · RESTORED(되돌림) · null 전체
     * @param nicknameLike 처리자 닉네임 부분 일치(소문자, LIKE 특수 글자 처리 끝 — 탈출 글자 {@code \}) — null 이면 거르지 않음
     */
    public record Filter(Long boardId, String kind, String restored, String nicknameLike, Instant from, Instant to) {
    }

    private final BoardStaffActionStore store;

    public AdminBoardStaffActionStore(JPAQueryFactory query, BoardStaffActionStore store) {
        super(query);
        this.store = store;
    }

    public long total(Filter filter) {
        JPAQuery<Long> count = query.select(ACTION.count()).from(ACTION);
        if (filter.nicknameLike() != null) {
            count = count.leftJoin(ACTOR).on(ACTOR.userId.eq(ACTION.actorId));
        }
        Long n = count.where(where(filter)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 최신 것이 위, {@code page} 0부터 */
    public List<ActionRow> page(Filter filter, int page, int size) {
        return store.actionRows().where(where(filter)).orderBy(ACTION.createdAt.desc(), ACTION.id.desc()).offset((long) page * size).limit(size).fetch().stream()
                .map(BoardStaffActionStore::row).toList();
    }

    /** 스텝 처리가 한 건이라도 있는 게시판(이름 차례) — 게시판 거르기 고르개 */
    public List<BoardRef> boards() {
        return query.select(B.id, B.slug, B.name, B.nameEn).from(B)
                .where(JPAExpressions.selectOne().from(ACTION).where(ACTION.boardId.eq(B.id), BoardStaffActionStore.content(ACTION)).exists())
                .orderBy(B.name.asc(), B.id.asc()).fetch().stream()
                .map(t -> new BoardRef(t.get(B.id), t.get(B.slug), t.get(B.name), t.get(B.nameEn))).toList();
    }

    private static BooleanExpression where(Filter f) {
        BooleanExpression where = BoardStaffActionStore.content(ACTION);
        if (f.boardId() != null) {
            where = where.and(ACTION.boardId.eq(f.boardId()));
        }
        if (f.kind() != null) {
            int cut = f.kind().indexOf('_');
            where = where.and(ACTION.kind.eq(f.kind().substring(0, cut))).and(ACTION.action.eq(f.kind().substring(cut + 1)));
        }
        if ("OPEN".equals(f.restored())) {
            where = where.and(ACTION.restoredAt.isNull());
        } else if ("RESTORED".equals(f.restored())) {
            where = where.and(ACTION.restoredAt.isNotNull());
        }
        if (f.nicknameLike() != null) {
            where = where.and(ACTOR.nickname.lower().like(f.nicknameLike(), '\\'));
        }
        if (f.from() != null) {
            where = where.and(ACTION.createdAt.goe(f.from()));
        }
        if (f.to() != null) {
            where = where.and(ACTION.createdAt.loe(f.to()));
        }
        return where;
    }
}
