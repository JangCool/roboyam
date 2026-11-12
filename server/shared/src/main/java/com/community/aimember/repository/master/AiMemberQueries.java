package com.community.aimember.repository.master;

import com.community.aimember.entity.AiDraftEntity;
import com.community.aimember.entity.AiDraftKind;
import com.community.aimember.entity.AiDraftStatus;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.QAiDraftEntity;
import com.community.aimember.entity.QAiMemberProfileEntity;
import com.community.comment.entity.QCommentEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * AI 회원 질의 — 작성자 표시(한 질의), 대기함·기록 목록, 실행기의 셈과 할 일 후보. 「AI 회원」 = {@code ai_member_profiles} 에 줄이 있는 회원.
 */
@Repository
public class AiMemberQueries extends IrobitRepositoryQDSL {

    private static final QAiMemberProfileEntity A = QAiMemberProfileEntity.aiMemberProfileEntity;
    private static final QAiDraftEntity D = QAiDraftEntity.aiDraftEntity;
    private static final QPostEntity P = QPostEntity.postEntity;
    private static final QCommentEntity C = QCommentEntity.commentEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final List<AiDraftStatus> COUNTING = List.of(AiDraftStatus.PENDING, AiDraftStatus.APPROVED, AiDraftStatus.PUBLISHED);

    public AiMemberQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 작성자 표시의 AI 칸 — 회원 ID → 제공사·모델(AI 회원이 아니면 빠진다) */
    public record AiAuthorRow(long userId, long aiMemberId, AiProvider provider, String model) {
    }

    /** 이 회원들 중 AI 회원 — 글 목록·댓글·프로필 글이 한 질의로 읽는다 */
    public Map<Long, AiAuthorRow> authorsOf(Collection<Long> userIds) {
        Map<Long, AiAuthorRow> out = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(A.userId, A.id, A.provider, A.model).from(A).where(A.userId.in(userIds)).fetch()) {
            out.put(t.get(A.userId), new AiAuthorRow(t.get(A.userId), t.get(A.id), t.get(A.provider), t.get(A.model)));
        }
        return out;
    }

    /** 모든 AI 회원의 회원 ID(수십 명 — 보상 이벤트·후보 거르기) */
    public Set<Long> allUserIds() {
        return new HashSet<>(query.select(A.userId).from(A).fetch());
    }

    public boolean isAi(long userId) {
        return query.selectOne().from(A).where(A.userId.eq(userId)).fetchFirst() != null;
    }

    /** 회원 ID 가 AI 회원이 아닌 조건(작성자 열) — 비회원 글(작성자 null)은 참 */
    public static BooleanExpression notAiAuthor(com.querydsl.core.types.dsl.NumberPath<Long> authorId) {
        return authorId.isNull().or(JPAExpressions.selectOne().from(A).where(A.userId.eq(authorId)).notExists());
    }

    // ── 셈 ────────────────────────────────────────────────────────────────

    /** 그 시각 뒤 만든 생성물 수(= 생성 호출 수 — 실패 포함) */
    public long callsSince(Instant since) {
        Long n = query.select(D.id.count()).from(D).where(D.createdAt.goe(since)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 계정별 그 시각 뒤 종류별 생성물 수(실패 포함 — 시도 수) */
    public Map<Long, Map<AiDraftKind, Long>> attemptsSince(Instant since) {
        Map<Long, Map<AiDraftKind, Long>> out = new HashMap<>();
        for (Tuple t : query.select(D.aiMemberId, D.kind, D.id.count()).from(D).where(D.createdAt.goe(since)).groupBy(D.aiMemberId, D.kind).fetch()) {
            out.computeIfAbsent(t.get(D.aiMemberId), k -> new HashMap<>()).put(t.get(D.kind), t.get(D.id.count()));
        }
        return out;
    }

    /** 계정별 그 시각 뒤 게시된 글·댓글 수(목록의 「오늘」) — {@code [글, 댓글+답글]} */
    public Map<Long, long[]> publishedSince(Instant since) {
        Map<Long, long[]> out = new HashMap<>();
        for (Tuple t : query.select(D.aiMemberId, D.kind, D.id.count()).from(D)
                .where(D.status.eq(AiDraftStatus.PUBLISHED), D.publishedAt.goe(since)).groupBy(D.aiMemberId, D.kind).fetch()) {
            long[] pair = out.computeIfAbsent(t.get(D.aiMemberId), k -> new long[2]);
            pair[t.get(D.kind) == AiDraftKind.POST ? 0 : 1] += t.get(D.id.count());
        }
        return out;
    }

    /** 계정별 검수 대기 수 */
    public Map<Long, Long> pendingByMember() {
        Map<Long, Long> out = new HashMap<>();
        for (Tuple t : query.select(D.aiMemberId, D.id.count()).from(D).where(D.status.eq(AiDraftStatus.PENDING)).groupBy(D.aiMemberId).fetch()) {
            out.put(t.get(D.aiMemberId), t.get(D.id.count()));
        }
        return out;
    }

    /** 계정의 마지막 시도 시각(종류별 — 없으면 빠진다) */
    public Map<AiDraftKind, Instant> lastAttempts(long aiMemberId) {
        Map<AiDraftKind, Instant> out = new HashMap<>();
        for (Tuple t : query.select(D.kind, D.createdAt.max()).from(D).where(D.aiMemberId.eq(aiMemberId)).groupBy(D.kind).fetch()) {
            out.put(t.get(D.kind), t.get(D.createdAt.max()));
        }
        return out;
    }

    // ── 글 쓰는 때(V191) ────────────────────────────────────────────────────

    /** 곳별 마지막 글 시각 — 게시판(소모임 글 아님) · AI 전용 소모임. 그 시각 뒤 글이 없는 곳은 빠진다 */
    public record LatestPosts(Map<Long, Instant> boards, Map<Long, Instant> groups) {
    }

    /**
     * 그 게시판·소모임들의 {@code since} 뒤 마지막 글 시각(살아 있는 글 · 숨김 아님 — 비밀글·19금도 센다, 한 곳마다 한 줄). {@code aiAuthorsOnly} 면 AI 회원이 쓴 글만.
     * 질의 두 번(게시판 · 소모임) — 색인 {@code ix_posts_board (board_id, id DESC)}·{@code ix_posts_group (group_id, id DESC)} 와 스노플레이크 id 하한으로 최근 범위만 읽는다.
     */
    public LatestPosts latestPosts(Collection<Long> boardIds, Collection<Long> groupIds, Instant since, Instant now, boolean aiAuthorsOnly) {
        BooleanExpression base = PostRepositoryQDSL.alive(P, now).and(PostRepositoryQDSL.notHidden(P, now))
                .and(P.id.goe(SnowflakeIdGenerator.floorIdAt(since))).and(P.createdAt.goe(since));
        if (aiAuthorsOnly) {
            base = base.and(JPAExpressions.selectOne().from(A).where(A.userId.eq(P.authorId)).exists());
        }
        Map<Long, Instant> boards = new HashMap<>();
        if (boardIds != null && !boardIds.isEmpty()) {
            for (Tuple t : query.select(P.boardId, P.createdAt.max()).from(P).where(base, P.boardId.in(boardIds), P.groupId.isNull()).groupBy(P.boardId).fetch()) {
                boards.put(t.get(P.boardId), t.get(P.createdAt.max()));
            }
        }
        Map<Long, Instant> groups = new HashMap<>();
        if (groupIds != null && !groupIds.isEmpty()) {
            for (Tuple t : query.select(P.groupId, P.createdAt.max()).from(P).where(base, P.groupId.in(groupIds)).groupBy(P.groupId).fetch()) {
                groups.put(t.get(P.groupId), t.get(P.createdAt.max()));
            }
        }
        return new LatestPosts(boards, groups);
    }

    /** 새 글을 지금 쓰지 않을 곳 — 게시판(소모임 글 아님) ID · 소모임 ID */
    public record BusyPlaces(Set<Long> boards, Set<Long> groups) {
    }

    /**
     * 새 글 생성 줄이 대기·승인 중인 곳 + {@code retrySince} 뒤 새 글을 시도한 곳(실패·버림 포함) — 한 곳에 두 계정이 겹쳐 쓰지 않게, 실패한 곳을 바퀴마다 다시 부르지 않게.
     * 색인 {@code ix_ai_member_drafts_status}·{@code ix_ai_member_drafts_created}.
     */
    public BusyPlaces postBusyPlaces(Instant retrySince) {
        Set<Long> boards = new HashSet<>();
        Set<Long> groups = new HashSet<>();
        // 두 번 나눠 읽는다(OR 로 묶으면 색인을 못 탄다)
        List<Tuple> rows = new java.util.ArrayList<>(query.select(D.boardId, D.groupId).from(D)
                .where(D.status.in(AiDraftStatus.PENDING, AiDraftStatus.APPROVED), D.kind.eq(AiDraftKind.POST)).fetch());
        rows.addAll(query.select(D.boardId, D.groupId).from(D).where(D.createdAt.goe(retrySince), D.kind.eq(AiDraftKind.POST)).fetch());
        for (Tuple t : rows) {
            if (t.get(D.groupId) != null) {
                groups.add(t.get(D.groupId));
            } else if (t.get(D.boardId) != null) {
                boards.add(t.get(D.boardId));
            }
        }
        return new BusyPlaces(boards, groups);
    }

    /** 계정별 마지막 새 글 시도 시각(어느 상태든 — 없으면 빠진다). 「가장 오래 글을 안 쓴 계정」 고르기 */
    public Map<Long, Instant> lastPostAttempts(Collection<Long> aiMemberIds) {
        Map<Long, Instant> out = new HashMap<>();
        if (aiMemberIds == null || aiMemberIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(D.aiMemberId, D.createdAt.max()).from(D).where(D.aiMemberId.in(aiMemberIds), D.kind.eq(AiDraftKind.POST)).groupBy(D.aiMemberId).fetch()) {
            out.put(t.get(D.aiMemberId), t.get(D.createdAt.max()));
        }
        return out;
    }

    // ── 대기함 · 기록 ────────────────────────────────────────────────────────

    /** 검수 대기 줄(최신이 위) — 종류·계정으로 거른다 */
    public List<AiDraftEntity> pending(AiDraftKind kind, Long aiMemberId, int limit) {
        BooleanExpression where = D.status.eq(AiDraftStatus.PENDING);
        if (kind != null) {
            where = where.and(D.kind.eq(kind));
        }
        if (aiMemberId != null) {
            where = where.and(D.aiMemberId.eq(aiMemberId));
        }
        return query.selectFrom(D).where(where).orderBy(D.id.desc()).limit(limit).fetch();
    }

    public long pendingTotal() {
        Long n = query.select(D.id.count()).from(D).where(D.status.eq(AiDraftStatus.PENDING)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 기록 한 쪽 */
    public record LogPage(List<AiDraftEntity> items, long total, long tokenSum) {
    }

    /**
     * 활동 기록 — 최신이 위.
     *
     * @param statuses 결과 거르기(빈 목록이면 전부)
     */
    public LogPage logs(Long aiMemberId, Collection<AiDraftStatus> statuses, AiDraftKind kind, int page, int size) {
        BooleanExpression where = D.id.isNotNull();
        if (aiMemberId != null) {
            where = where.and(D.aiMemberId.eq(aiMemberId));
        }
        if (statuses != null && !statuses.isEmpty()) {
            where = where.and(D.status.in(statuses));
        }
        if (kind != null) {
            where = where.and(D.kind.eq(kind));
        }
        var tokens = D.inputTokens.add(D.outputTokens).longValue().sum();
        Tuple sums = query.select(D.id.count(), tokens).from(D).where(where).fetchOne();
        long total = sums == null || sums.get(D.id.count()) == null ? 0 : sums.get(D.id.count());
        Long tokenSum = sums == null ? null : sums.get(tokens);
        List<AiDraftEntity> items = query.selectFrom(D).where(where).orderBy(D.id.desc()).offset((long) (page - 1) * size).limit(size).fetch();
        return new LogPage(items, total, tokenSum == null ? 0 : tokenSum);
    }

    // ── 실행기의 할 일 후보 ───────────────────────────────────────────────────

    /** 최근 글 한 줄(후보) */
    public record PostCandidate(long postId, long boardId, Long groupId, long authorId, String title, String contentText, Instant createdAt) {
    }

    /**
     * 다른 AI 의 최근 글(첫 댓글 후보) — 보이는 글 · 숨김 아님 · 비밀글 아님 · 19금 아님, {@code since}~{@code until} 에 쓴 것, 작성자가 AI 회원(나 빼고).
     * {@code includeMembers} 면 일반 회원 글도(설정 「회원 글 댓글 허용」 — 기본 꺼짐). 게시판·소모임 거르기는 부르는 쪽이 한다.
     */
    public List<PostCandidate> recentPosts(long exceptUserId, boolean includeMembers, Instant since, Instant until, Instant now, int limit) {
        BooleanExpression where = PostRepositoryQDSL.alive(P, now).and(PostRepositoryQDSL.notHidden(P, now)).and(P.secret.isFalse()).and(P.adult.isFalse())
                .and(P.authorId.isNotNull()).and(P.authorId.ne(exceptUserId))
                .and(P.id.goe(SnowflakeIdGenerator.floorIdAt(since))).and(P.createdAt.goe(since)).and(P.createdAt.loe(until));
        if (!includeMembers) {
            where = where.and(JPAExpressions.selectOne().from(A).where(A.userId.eq(P.authorId)).exists());
        }
        return query.select(P.id, P.boardId, P.groupId, P.authorId, P.title, P.contentText, P.createdAt).from(P).where(where).orderBy(P.id.desc()).limit(limit).fetch()
                .stream().map(t -> new PostCandidate(t.get(P.id), t.get(P.boardId), t.get(P.groupId), t.get(P.authorId), t.get(P.title), t.get(P.contentText),
                        t.get(P.createdAt))).toList();
    }

    /**
     * 뉴스 글 소재 후보(ai-members-contract 「뉴스 글·프롬프트 관리」) — 그 말머리를 단 게시판 글, {@code since}~{@code until} 에 쓴 것, 최신이 위.
     * 사람 회원 글만(비회원·AI 회원 글 제외) · 보이는 글 · 숨김 아님 · 비밀글 아님 · 19금 아님 · 익명 글 아님 · 소모임 글 아님(비회원이 못 읽을 수 있다) ·
     * 이미 다른 생성 줄의 소재로 쓴 글 아님(실패 줄만 빼고 센다 — 대기·승인·게시·버림 모두). 게시판·말머리 거르기(쓸 수 있는 일반 게시판)는 부르는 쪽이 정한다.
     */
    public List<PostCandidate> newsCandidates(Collection<Long> prefixIds, Collection<Long> boardIds, Instant since, Instant until, Instant now, int limit) {
        if (prefixIds == null || prefixIds.isEmpty() || boardIds == null || boardIds.isEmpty()) {
            return List.of();
        }
        BooleanExpression where = PostRepositoryQDSL.alive(P, now).and(PostRepositoryQDSL.notHidden(P, now)).and(P.secret.isFalse()).and(P.adult.isFalse())
                .and(P.anonymous.isFalse()).and(P.groupId.isNull()).and(P.authorId.isNotNull())
                .and(P.prefixId.in(prefixIds)).and(P.boardId.in(boardIds))
                .and(P.id.goe(SnowflakeIdGenerator.floorIdAt(since))).and(P.createdAt.goe(since)).and(P.createdAt.loe(until))
                .and(JPAExpressions.selectOne().from(A).where(A.userId.eq(P.authorId)).notExists())
                .and(JPAExpressions.selectOne().from(D).where(D.sourcePostId.eq(P.id), D.status.ne(AiDraftStatus.FAILED)).notExists());
        return query.select(P.id, P.boardId, P.groupId, P.authorId, P.title, P.contentText, P.createdAt).from(P).where(where).orderBy(P.id.desc()).limit(limit).fetch()
                .stream().map(t -> new PostCandidate(t.get(P.id), t.get(P.boardId), t.get(P.groupId), t.get(P.authorId), t.get(P.title), t.get(P.contentText),
                        t.get(P.createdAt))).toList();
    }

    /** 내 최근 글(답글 후보를 찾을 글) */
    public List<PostCandidate> myRecentPosts(long userId, Instant since, Instant now, int limit) {
        BooleanExpression where = PostRepositoryQDSL.alive(P, now).and(PostRepositoryQDSL.notHidden(P, now)).and(P.authorId.eq(userId))
                .and(P.id.goe(SnowflakeIdGenerator.floorIdAt(since))).and(P.createdAt.goe(since));
        return query.select(P.id, P.boardId, P.groupId, P.authorId, P.title, P.contentText, P.createdAt).from(P).where(where).orderBy(P.id.desc()).limit(limit).fetch()
                .stream().map(t -> new PostCandidate(t.get(P.id), t.get(P.boardId), t.get(P.groupId), t.get(P.authorId), t.get(P.title), t.get(P.contentText),
                        t.get(P.createdAt))).toList();
    }

    /** 글 하나의 보이는 댓글 한 줄 */
    public record CommentRow(long id, long postId, Long parentId, Long replyToId, Long authorId, String content, String format, Instant createdAt) {
    }

    /** 그 글들의 살아 있는(지우지·숨기지 않은) 회원 댓글 — 오래된 것이 위 */
    public List<CommentRow> liveComments(Collection<Long> postIds, Instant now) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        return query.select(C.id, C.postId, C.parentId, C.replyToId, C.authorId, C.content, C.contentFormat, C.createdAt).from(C)
                .where(C.postId.in(postIds), C.deletedAt.isNull(), C.authorId.isNotNull(), C.hiddenAt.isNull().or(C.hiddenUntil.isNotNull().and(C.hiddenUntil.loe(now))))
                .orderBy(C.id.asc()).fetch().stream()
                .map(t -> new CommentRow(t.get(C.id), t.get(C.postId), t.get(C.parentId), t.get(C.replyToId), t.get(C.authorId), t.get(C.content),
                        t.get(C.contentFormat) == null ? null : t.get(C.contentFormat).name(), t.get(C.createdAt))).toList();
    }

    /** 그 글들에 달린(달릴) AI 생성물 — 셈에 드는 상태(대기·승인·게시)만 */
    public List<AiDraftEntity> liveDraftsOn(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        return query.selectFrom(D).where(D.postId.in(postIds), D.status.in(COUNTING)).fetch();
    }

    /** 내가 그 시각 뒤 게시한 댓글·답글이 있는 글(상대의 되받기를 찾을 곳) */
    public List<Long> commentedPostIds(long aiMemberId, Instant since) {
        return query.select(D.postId).distinct().from(D)
                .where(D.aiMemberId.eq(aiMemberId), D.kind.in(AiDraftKind.COMMENT, AiDraftKind.REPLY), D.status.eq(AiDraftStatus.PUBLISHED), D.publishedAt.goe(since),
                        D.postId.isNotNull()).fetch();
    }

    /** 내가 만든 최근 글 제목(겹치지 않게 프롬프트에 — AI 가 만든 글이라 회원 정보가 아니다) */
    public List<String> recentTitles(long aiMemberId, int limit) {
        return query.select(D.title).from(D).where(D.aiMemberId.eq(aiMemberId), D.kind.eq(AiDraftKind.POST), D.title.isNotNull(), D.status.in(COUNTING))
                .orderBy(D.id.desc()).limit(limit).fetch();
    }

    /** 실행기가 등록할 승인 줄(오래된 것부터) */
    public List<Long> approvedIds(int limit) {
        return query.select(D.id).from(D).where(D.status.eq(AiDraftStatus.APPROVED)).orderBy(D.id.asc()).limit(limit).fetch();
    }

    /** 게시된 글·댓글을 만든 생성 줄의 제공사·모델 */
    public record ContentModel(AiProvider provider, String model) {
    }

    /** 게시된 글 번호 → 만든 제공사·모델(생성 줄이 없는 글은 빠진다 — 한 질의) */
    public Map<Long, ContentModel> postModels(Collection<Long> postIds) {
        Map<Long, ContentModel> out = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(D.publishedPostId, D.provider, D.model).from(D).where(D.publishedPostId.in(postIds)).fetch()) {
            out.put(t.get(D.publishedPostId), new ContentModel(t.get(D.provider), t.get(D.model)));
        }
        return out;
    }

    /** 게시된 댓글 번호 → 만든 제공사·모델(한 질의) */
    public Map<Long, ContentModel> commentModels(Collection<Long> commentIds) {
        Map<Long, ContentModel> out = new HashMap<>();
        if (commentIds == null || commentIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(D.publishedCommentId, D.provider, D.model).from(D).where(D.publishedCommentId.in(commentIds)).fetch()) {
            out.put(t.get(D.publishedCommentId), new ContentModel(t.get(D.provider), t.get(D.model)));
        }
        return out;
    }

    /** 공개 글 하나를 만든 생성물의 모델(글 보기 고지 — 없으면 null) */
    public AiDraftEntity draftOfPost(long postId) {
        return query.selectFrom(D).where(D.publishedPostId.eq(postId)).fetchFirst();
    }

    /** 글 머리 — 대기함·기록의 대상 한 줄 */
    public record PostHead(long id, long boardId, Long groupId, Long authorId, String title, String contentText) {
    }

    /** 글 번호 → 머리(지운 글도) */
    public Map<Long, PostHead> postHeads(Collection<Long> postIds) {
        Map<Long, PostHead> out = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(P.id, P.boardId, P.groupId, P.authorId, P.title, P.contentText).from(P).where(P.id.in(postIds)).fetch()) {
            out.put(t.get(P.id), new PostHead(t.get(P.id), t.get(P.boardId), t.get(P.groupId), t.get(P.authorId), t.get(P.title), t.get(P.contentText)));
        }
        return out;
    }

    /** 댓글 번호 → 줄(지운 댓글도 — 지웠으면 본문이 비어 있을 수 있다) */
    public Map<Long, CommentRow> commentsByIds(Collection<Long> commentIds) {
        Map<Long, CommentRow> out = new HashMap<>();
        if (commentIds == null || commentIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(C.id, C.postId, C.parentId, C.replyToId, C.authorId, C.content, C.contentFormat, C.createdAt).from(C).where(C.id.in(commentIds)).fetch()) {
            out.put(t.get(C.id), new CommentRow(t.get(C.id), t.get(C.postId), t.get(C.parentId), t.get(C.replyToId), t.get(C.authorId), t.get(C.content),
                    t.get(C.contentFormat) == null ? null : t.get(C.contentFormat).name(), t.get(C.createdAt)));
        }
        return out;
    }

    // ── 이름 ──────────────────────────────────────────────────────────────

    /** 운영자 이름 — 번호 → 이름(지운 운영자는 빠진다) */
    public Map<Long, String> operatorNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(O.id, O.operatorName).from(O).where(O.id.in(ids)).fetch()) {
            out.put(t.get(O.id), t.get(O.operatorName));
        }
        return out;
    }

    /** 회원 닉네임 — 회원 ID → 닉네임(관리자 화면 전용 — 제공사에는 보내지 않는다) */
    public Map<Long, String> nicknames(Collection<Long> userIds) {
        Map<Long, String> out = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(PR.userId, PR.nickname).from(PR).where(PR.userId.in(userIds)).fetch()) {
            out.put(t.get(PR.userId), t.get(PR.nickname));
        }
        return out;
    }

    /** 회원이 지금 활동을 멈춰야 하는가(정지 · 영구 정지 · 탈퇴 진행 · 탈퇴) — 회원 ID → 멈춤 */
    public Map<Long, Boolean> restricted(Collection<Long> userIds) {
        Map<Long, Boolean> out = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(U.id, U.status, U.deletedAt).from(U).where(U.id.in(userIds)).fetch()) {
            UserStatus st = t.get(U.status);
            out.put(t.get(U.id), t.get(U.deletedAt) != null || st == UserStatus.SUSPENDED || st == UserStatus.BANNED || st == UserStatus.WITHDRAWING
                    || st == UserStatus.WITHDRAWN);
        }
        return out;
    }
}
