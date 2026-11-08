package com.community.board.repository.master;

import com.community.comment.entity.QCommentDislikeEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.comment.entity.QCommentLikeEntity;
import com.community.post.entity.PostEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.entity.QPostLikeEntity;
import com.community.post.entity.QPostProcessStatusHistoryEntity;
import com.community.post.entity.QPostViewEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 게시판 관리가 읽는 글·댓글 집계(글 수·말머리 사용 수·삭제 가능 판정)와 게시판을 지울 때의 글 정리. 트랜잭션은 부르는 서비스의 것을 따른다.
 *
 * <p>글·댓글({@code posts} 월 파티션 · {@code comments} 해시 파티션)은 엔티티로 집계·일괄 수정·일괄 삭제한다(엔티티를 올리지 않는 대량 문장).
 * 추천·조회 기록 표({@code post_likes}·{@code post_views}·{@code comment_likes}·{@code comment_dislikes})도 엔티티로 일괄 삭제한다(글 ID 를 이 게시판의 글에서 고르는 부분 질의).
 */
@Repository
public class BoardContentQueries extends RoboyamRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity COMMENT = QCommentEntity.commentEntity;
    private static final QPostLikeEntity POST_LIKE = QPostLikeEntity.postLikeEntity;
    private static final QPostViewEntity POST_VIEW = QPostViewEntity.postViewEntity;
    private static final QCommentLikeEntity COMMENT_LIKE = QCommentLikeEntity.commentLikeEntity;
    private static final QCommentDislikeEntity COMMENT_DISLIKE = QCommentDislikeEntity.commentDislikeEntity;
    private static final QPostProcessStatusHistoryEntity STATUS_HISTORY = QPostProcessStatusHistoryEntity.postProcessStatusHistoryEntity;

    public BoardContentQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 게시판별 글 수 — 삭제되지 않은 게시된 글(숨김 포함, 임시저장 제외). 글이 없는 게시판은 빠진다 */
    public Map<Long, Long> postCounts() {
        Map<Long, Long> out = new HashMap<>();
        for (Tuple t : query.select(POST.boardId, POST.count()).from(POST).where(POST.deletedAt.isNull(), POST.status.eq(PostEntity.PUBLISHED))
                .groupBy(POST.boardId).fetch()) {
            out.put(t.get(POST.boardId), t.get(POST.count()));
        }
        return out;
    }

    public long postCount(long boardId) {
        Long count = query.select(POST.count()).from(POST).where(POST.boardId.eq(boardId), POST.deletedAt.isNull(), POST.status.eq(PostEntity.PUBLISHED)).fetchOne();
        return count == null ? 0 : count;
    }

    /** 삭제되지 않은 글(임시저장 포함)이 하나라도 있는가 — 있으면 게시판을 지울 수 없다 */
    public boolean hasLivePosts(long boardId) {
        return query.selectOne().from(POST).where(POST.boardId.eq(boardId), POST.deletedAt.isNull()).fetchFirst() != null;
    }

    /** 이 게시판의 말머리별 사용 수(삭제·임시저장 포함) */
    public Map<Long, Long> prefixUsage(long boardId) {
        Map<Long, Long> out = new HashMap<>();
        for (Tuple t : query.select(POST.prefixId, POST.count()).from(POST).where(POST.boardId.eq(boardId), POST.prefixId.isNotNull())
                .groupBy(POST.prefixId).fetch()) {
            out.put(t.get(POST.prefixId), t.get(POST.count()));
        }
        return out;
    }

    /**
     * 성인 전용으로 새로 켠 말머리의 글을 19금으로 바꾼다(adult-content-contract — 말머리 「성인 전용」). 끈 말머리의 글은 되돌리지 않는다(글쓴이가 따로 19금으로 고른 글일 수 있다).
     * {@code ix_posts_board_prefix} 를 탄다.
     *
     * @return 바꾼 글 수
     */
    public int markAdult(long boardId, Collection<Long> prefixIds) {
        if (prefixIds.isEmpty()) {
            return 0;
        }
        int changed = 0;
        for (Long prefixId : prefixIds) {
            changed += (int) query.update(POST).set(POST.adult, true).where(POST.boardId.eq(boardId), POST.prefixId.eq(prefixId), POST.adult.isFalse()).execute();
        }
        return changed;
    }

    /**
     * 게시판을 지울 때 남은(삭제된) 글·댓글과 그 글에 딸린 추천·조회 기록을 함께 지운다 — 파티션 표라 외래 키 연쇄 삭제가 없다. 글을 지우기 전에 딸린 행부터 지운다
     * (글 ID 를 이 게시판의 글에서 고른다 — {@code ix_posts_board_prefix} 의 {@code board_id} 앞부분을 탄다).
     * <ul>
     *   <li>{@code post_likes}·{@code post_views}: 글 ID 해시 조각 — {@code post_id} 가 PK 앞부분이라 조각마다 PK 색인으로 찾는다.</li>
     *   <li>{@code comment_likes}·{@code comment_dislikes}: 댓글 ID 해시 조각이지만 {@code post_id} 칸과 {@code (post_id, user_id)} 색인이 있어 글 ID 로 찾는다.</li>
     *   <li><b>신고({@code reports})는 지우지 않는다</b> — 신고자·당시 내용 스냅샷은 증거라 게시판이 지워져도 남기고, 개인정보 보유기간 배치(신고 기록 축 {@code R__batch_18})가
     *       기간이 지나면 지운다.</li>
     * </ul>
     * 부분 질의는 {@code post_id IN (SELECT id FROM posts WHERE board_id = ?)} 로 예전 SQL 과 같다.
     */
    public void deleteContent(long boardId) {
        query.delete(POST_LIKE).where(POST_LIKE.postId.in(boardPostIds(boardId))).execute();
        query.delete(COMMENT_LIKE).where(COMMENT_LIKE.postId.in(boardPostIds(boardId))).execute();
        query.delete(COMMENT_DISLIKE).where(COMMENT_DISLIKE.postId.in(boardPostIds(boardId))).execute();
        query.delete(POST_VIEW).where(POST_VIEW.postId.in(boardPostIds(boardId))).execute();
        query.delete(STATUS_HISTORY).where(STATUS_HISTORY.postId.in(boardPostIds(boardId))).execute(); // 처리 상태 이력(V168 — 글과 함께 없어진다)
        query.delete(COMMENT).where(COMMENT.boardId.eq(boardId)).execute();
        query.delete(POST).where(POST.boardId.eq(boardId)).execute();
    }

    /** 이 게시판의 글 ID(부분 질의) */
    private static JPQLQuery<Long> boardPostIds(long boardId) {
        QPostEntity p = new QPostEntity("boardPost");
        return JPAExpressions.select(p.id).from(p).where(p.boardId.eq(boardId));
    }
}
