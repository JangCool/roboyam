package com.community.notice.repository.master;

import com.community.notice.dto.NoticeGlobalPinRow;
import com.community.notice.entity.QNoticeGlobalPinEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 전체 공지 질의(V174) — <b>살아 있는</b> 전체 공지 = 줄이 있고, 그 글이 공지 게시판의 일반 글(소모임 글 아님)이며 보이는 글({@link PostRepositoryQDSL#alive})이고
 * 숨김 중이 아니고({@link PostRepositoryQDSL#notHidden}) 비밀글 · 19금이 아니며, 글의 고정 시각이 줄의 사본과 같고, 기간이 남았다(끝 없음 포함).
 * 게시판이 켜졌는지는 부르는 쪽이 본다(게시판 사본).
 */
@Repository
public class NoticeGlobalPinQueries extends IrobitRepositoryQDSL {

    private static final QNoticeGlobalPinEntity G = QNoticeGlobalPinEntity.noticeGlobalPinEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;

    public NoticeGlobalPinQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 살아 있는 전체 공지 — 고정 시각 최근 것이 위, 최대 {@code limit} 개 */
    public List<NoticeGlobalPinRow> live(long noticeBoardId, Instant now, int limit) {
        List<Tuple> rows = query.select(G.postId, POST.title, POST.createdAt, G.pinnedAt, G.excludedBoardIds, G.excludeGroupBoards, G.expiresAt)
                .from(G).join(POST).on(POST.id.eq(G.postId))
                .where(liveWhere(noticeBoardId, now))
                .orderBy(G.pinnedAt.desc(), G.postId.desc())
                .limit(limit)
                .fetch();
        return rows.stream().map(t -> {
            Long[] excluded = t.get(G.excludedBoardIds);
            return new NoticeGlobalPinRow(t.get(G.postId), t.get(POST.title), t.get(POST.createdAt), t.get(G.pinnedAt),
                    excluded == null ? List.of() : List.copyOf(Arrays.asList(excluded)), Boolean.TRUE.equals(t.get(G.excludeGroupBoards)), t.get(G.expiresAt));
        }).toList();
    }

    /** 살아 있는 전체 공지 수 — {@code exceptPostId} 글은 세지 않는다(그 글을 고칠 때) */
    public long liveCount(long noticeBoardId, long exceptPostId, Instant now) {
        Long n = query.select(G.postId.count()).from(G).join(POST).on(POST.id.eq(G.postId))
                .where(liveWhere(noticeBoardId, now), G.postId.ne(exceptPostId))
                .fetchOne();
        return n == null ? 0 : n;
    }

    private static BooleanExpression liveWhere(long noticeBoardId, Instant now) {
        return POST.boardId.eq(noticeBoardId).and(POST.groupId.isNull()).and(POST.pinnedAt.eq(G.pinnedAt))
                .and(PostRepositoryQDSL.alive(POST, now)).and(PostRepositoryQDSL.notHidden(POST, now))
                .and(POST.secret.isFalse()).and(POST.adult.isFalse())
                .and(G.expiresAt.isNull().or(G.expiresAt.gt(now)));
    }
}
