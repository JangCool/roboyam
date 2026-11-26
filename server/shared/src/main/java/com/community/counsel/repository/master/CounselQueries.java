package com.community.counsel.repository.master;

import com.community.aimember.entity.AiDraftEntity;
import com.community.aimember.entity.AiDraftKind;
import com.community.aimember.entity.QAiDraftEntity;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.comment.entity.QCommentEntity;
import com.community.counsel.entity.CounselPostEntity;
import com.community.counsel.entity.CounselState;
import com.community.counsel.entity.QCounselPostEntity;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * AI 연애상담소 질의(V274) — 실행기의 새 사연·열린 사연, 하루 상한 셈, 상담사 답(그 글의 상담사 원댓글)·생성 줄. 「상담 글」 = 「AI 상담소」 말머리({@code board_prefixes.counsel})를 단 글.
 * 글 표는 id(Snowflake = 시각) 범위 파티션이라 시각 조건에 id 하한을 같이 건다.
 */
@Repository
public class CounselQueries extends IrobitRepositoryQDSL {

    private static final QPostEntity P = QPostEntity.postEntity;
    private static final QCommentEntity C = QCommentEntity.commentEntity;
    private static final QAiDraftEntity D = QAiDraftEntity.aiDraftEntity;
    private static final QCounselPostEntity CP = QCounselPostEntity.counselPostEntity;

    public CounselQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 판정할 새 사연 — 인용에 넣을 제목·평문 본문까지 */
    public record NewPost(long postId, long boardId, long authorId, String title, String contentText, Instant createdAt) {
    }

    /**
     * 아직 판정하지 않은 상담 글 — {@code since}~{@code until} 에 쓴 것, 오래된 것이 위. 보이는 글 · 숨김 아님 · 비밀글 아님 · 소모임 글 아님 · 회원 글(비회원·AI 회원 글 제외) ·
     * AI 제외를 켜지 않은 회원 · {@code counsel_posts} 줄 없음. 제목과 보이는 글자({@code contentText} 칸 = HTML 글은 평문, TEXT 글은 본문)를 싣는다.
     */
    public List<NewPost> untriaged(Collection<Long> prefixIds, Instant since, Instant until, Instant now, int limit) {
        if (prefixIds == null || prefixIds.isEmpty()) {
            return List.of();
        }
        BooleanExpression where = PostRepositoryQDSL.alive(P, now).and(PostRepositoryQDSL.notHidden(P, now)).and(P.secret.isFalse()).and(P.groupId.isNull())
                .and(P.prefixId.in(prefixIds)).and(P.authorId.isNotNull()).and(AiMemberQueries.notAiAuthor(P.authorId))
                .and(AiMemberQueries.notOptedOut(P.authorId)) // 「내 글·댓글을 AI 회원에게 보내지 않기」(V264)를 켠 회원의 사연은 제공사로 보내지 않는다(글쓰기에서도 막는다)
                .and(P.id.goe(SnowflakeIdGenerator.floorIdAt(since))).and(P.createdAt.goe(since)).and(P.createdAt.loe(until))
                .and(JPAExpressions.selectOne().from(CP).where(CP.postId.eq(P.id)).notExists());
        return query.select(P.id, P.boardId, P.authorId, P.title, P.content, P.contentText, P.contentFormat, P.createdAt).from(P).where(where).orderBy(P.id.asc())
                .limit(limit).fetch().stream()
                .map(t -> new NewPost(t.get(P.id), t.get(P.boardId), t.get(P.authorId), t.get(P.title),
                        // 보이는 글자 — HTML 글은 저장해 둔 평문, TEXT 글은 본문 그대로(PostEntity#plainContent 와 같다)
                        t.get(P.contentFormat) == ContentFormat.HTML && t.get(P.contentText) != null ? t.get(P.contentText) : t.get(P.content), t.get(P.createdAt)))
                .toList();
    }

    /** 아직 끝나지 않은 사연(판정 대기 · 답하는 중) — 오래된 것이 위 */
    public List<CounselPostEntity> openRows(int limit) {
        return query.selectFrom(CP).where(CP.closedAt.isNull()).orderBy(CP.createdAt.asc()).limit(limit).fetch();
    }

    /** 그 시각 뒤 판정을 지나 답하기 시작했거나 기다리는 사연 수(위험 사연 제외) — 실행기의 사이트 하루 상한 */
    public long answeringSince(Instant since) {
        Long n = query.select(CP.postId.count()).from(CP).where(CP.createdAt.goe(since), CP.state.ne(CounselState.RISK)).fetchOne();
        return n == null ? 0 : n;
    }

    /**
     * 그 시각 뒤 올라온 상담 글 수 — 글쓰기 때의 하루 상한(지운 글도 센다 — 지우고 다시 올려 상한을 넘지 않게). {@code authorId} 가 null 이면 사이트 전체.
     *
     * @param exceptPostId 셈에서 뺄 글(고치기 — 이미 상담 글이면 자기 자신), 없으면 null
     */
    public long postsSince(Collection<Long> prefixIds, Long authorId, Instant since, Long exceptPostId) {
        if (prefixIds == null || prefixIds.isEmpty()) {
            return 0;
        }
        BooleanExpression where = P.prefixId.in(prefixIds).and(P.status.eq(PostEntity.PUBLISHED)).and(P.groupId.isNull())
                .and(P.id.goe(SnowflakeIdGenerator.floorIdAt(since))).and(P.createdAt.goe(since));
        if (authorId != null) {
            where = where.and(P.authorId.eq(authorId));
        }
        if (exceptPostId != null) {
            where = where.and(P.id.ne(exceptPostId));
        }
        Long n = query.select(P.id.count()).from(P).where(where).fetchOne();
        return n == null ? 0 : n;
    }

    /** 상담사 답 하나 — 그 글에 상담사가 단 원댓글(지운 것 제외, 숨긴 것은 포함 — 화면이 숨김 자리로 그린다) */
    public record Answer(long commentId, long postId, long authorId, Instant createdAt) {
    }

    /** 그 글들의 상담사 답 — 글 → 답(오래된 것이 위). 한 상담사가 두 번 단 일은 없어야 하지만 있으면 둘 다 온다(부르는 쪽이 첫 것을 쓴다) */
    public Map<Long, List<Answer>> answers(Collection<Long> postIds, Collection<Long> counselorUserIds) {
        Map<Long, List<Answer>> out = new HashMap<>();
        if (postIds == null || postIds.isEmpty() || counselorUserIds == null || counselorUserIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(C.id, C.postId, C.authorId, C.createdAt).from(C)
                .where(C.postId.in(postIds), C.parentId.isNull(), C.deletedAt.isNull(), C.authorId.in(counselorUserIds)).orderBy(C.id.asc()).fetch()) {
            out.computeIfAbsent(t.get(C.postId), k -> new ArrayList<>()).add(new Answer(t.get(C.id), t.get(C.postId), t.get(C.authorId), t.get(C.createdAt)));
        }
        return out;
    }

    /** 그 글들의 상담사 답 수(글 목록 줄의 {@code aiAnswerCount}) — 답이 없는 글은 빠진다 */
    public Map<Long, Long> answerCounts(Collection<Long> postIds, Collection<Long> counselorUserIds) {
        Map<Long, Long> out = new HashMap<>();
        if (postIds == null || postIds.isEmpty() || counselorUserIds == null || counselorUserIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(C.postId, C.id.count()).from(C)
                .where(C.postId.in(postIds), C.parentId.isNull(), C.deletedAt.isNull(), C.authorId.in(counselorUserIds)).groupBy(C.postId).fetch()) {
            out.put(t.get(C.postId), t.get(C.id.count()));
        }
        return out;
    }

    /** 그 글에 상담사들이 만든 댓글 생성 줄(모든 상태 — 실패 포함, 오래된 것이 위) */
    public List<AiDraftEntity> drafts(long postId, Collection<Long> aiMemberIds) {
        if (aiMemberIds == null || aiMemberIds.isEmpty()) {
            return List.of();
        }
        return query.selectFrom(D).where(D.postId.eq(postId), D.kind.eq(AiDraftKind.COMMENT), D.aiMemberId.in(aiMemberIds)).orderBy(D.id.asc()).fetch();
    }
}
