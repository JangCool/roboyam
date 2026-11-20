package com.community.jjal.repository.master;

import com.community.comment.entity.QCommentEntity;
import com.community.jjal.entity.JjalBoxItemEntity;
import com.community.jjal.entity.JjalFileEntity;
import com.community.jjal.entity.JjalFileStatus;
import com.community.jjal.entity.QJjalBoxItemEntity;
import com.community.jjal.entity.QJjalFileEntity;
import com.community.jjal.entity.QJjalUploadEventEntity;
import com.community.jjal.entity.QJjalUseEntity;
import com.community.post.entity.QPostEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 내 짤 질의 — 보관함(순서·개수), 올리기 속도, 쓰임(글·댓글), 정리 배치 후보. docs/features/my-jjal-contract.md.
 * 색인: 보관함 {@code ix_jjal_box_items_user_sort} · 파일의 보관함 줄 {@code ix_jjal_box_items_file} · 올리기 {@code ix_jjal_upload_events_user} · 쓰임 {@code ux_jjal_uses_target}·{@code ix_jjal_uses_file}.
 */
@Repository
public class JjalRepositoryQDSL extends IrobitRepositoryQDSL {

    private static final QJjalFileEntity FILE = QJjalFileEntity.jjalFileEntity;
    private static final QJjalBoxItemEntity ITEM = QJjalBoxItemEntity.jjalBoxItemEntity;
    private static final QJjalUploadEventEntity UPLOAD = QJjalUploadEventEntity.jjalUploadEventEntity;
    private static final QJjalUseEntity USE = QJjalUseEntity.jjalUseEntity;

    public JjalRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 보관함 줄과 그 파일 */
    public record BoxRow(JjalBoxItemEntity item, JjalFileEntity file) {
    }

    /** 내 보관함 — 회원이 정한 순서(한 문장: 줄과 파일을 함께 읽는다) */
    public List<BoxRow> box(long userId) {
        List<Tuple> rows = query.select(ITEM, FILE).from(ITEM).join(FILE).on(FILE.id.eq(ITEM.fileId))
                .where(ITEM.userId.eq(userId))
                .orderBy(ITEM.sortKey.asc(), ITEM.id.asc())
                .fetch();
        List<BoxRow> out = new ArrayList<>(rows.size());
        for (Tuple t : rows) {
            out.add(new BoxRow(t.get(ITEM), t.get(FILE)));
        }
        return out;
    }

    /** 보관함의 가장 앞 순서 값(비었으면 null) — 새 줄은 이 값 − 1 */
    public Long minSortKey(long userId) {
        return query.select(ITEM.sortKey.min()).from(ITEM).where(ITEM.userId.eq(userId)).fetchOne();
    }

    /** 이 파일들 중 이 회원 보관함에 있는 것 */
    public Set<Long> inBox(long userId, Collection<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(query.select(ITEM.fileId).from(ITEM).where(ITEM.userId.eq(userId), ITEM.fileId.in(fileIds)).fetch());
    }

    // ── 올리기 속도 ─────────────────────────────────────────────────────────

    /** {@code since} 뒤에 올린 수 */
    public long uploadsSince(long userId, Instant since) {
        Long n = query.select(UPLOAD.id.count()).from(UPLOAD).where(UPLOAD.userId.eq(userId), UPLOAD.createdAt.gt(since)).fetchOne();
        return n == null ? 0 : n;
    }

    /** {@code since} 뒤의 가장 이른 올리기 시각(없으면 null) — 다시 시도할 때까지 남은 시간 */
    public Instant oldestUploadSince(long userId, Instant since) {
        return query.select(UPLOAD.createdAt.min()).from(UPLOAD).where(UPLOAD.userId.eq(userId), UPLOAD.createdAt.gt(since)).fetchOne();
    }

    /** 정리 — {@code before} 보다 오래된 올리기 기록 */
    public long deleteUploadsBefore(Instant before) {
        return query.delete(UPLOAD).where(UPLOAD.createdAt.lt(before)).execute();
    }

    // ── 쓰임 ────────────────────────────────────────────────────────────────

    private static BooleanExpression target(long postId, Long commentId) {
        return USE.postId.eq(postId).and(commentId == null ? USE.commentId.isNull() : USE.commentId.eq(commentId));
    }

    /** 글 본문(댓글 ID null) 또는 댓글 하나의 쓰임을 모두 지운다 — 저장 때 다시 쓰기 전에 */
    public long deleteUses(long postId, Long commentId) {
        return query.delete(USE).where(target(postId, commentId)).execute();
    }

    /** 그 글 본문(댓글 ID null)·그 댓글에 이 파일이 쓰였는가 */
    public boolean usedAt(long fileId, long postId, Long commentId) {
        return query.selectOne().from(USE).where(USE.fileId.eq(fileId), target(postId, commentId)).fetchFirst() != null;
    }

    /** 이 파일들 중 그 글(본문·댓글 어디든)에 쓰인 것 */
    public Set<Long> usedInPost(Collection<Long> fileIds, long postId) {
        if (fileIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(query.selectDistinct(USE.fileId).from(USE).where(USE.postId.eq(postId), USE.fileId.in(fileIds)).fetch());
    }

    // ── 정리 배치 ───────────────────────────────────────────────────────────

    /**
     * 더는 살아 있지 않은 쓰임(ID 순) — 글을 지웠거나 예약 삭제 시각이 지났거나 글 행이 없어졌다, 또는 댓글을 지웠거나 댓글 행이 없어졌다.
     * 공개 상태가 아닌 글(판매자 정지 등)은 지운 글이 아니라 남긴다.
     */
    public List<Long> deadUses(Instant now, int limit) {
        QPostEntity post = QPostEntity.postEntity;
        QCommentEntity comment = QCommentEntity.commentEntity;
        BooleanExpression postAlive = JPAExpressions.selectOne().from(post)
                .where(post.id.eq(USE.postId), post.deletedAt.isNull(), post.deleteScheduledAt.isNull().or(post.deleteScheduledAt.gt(now))).exists();
        BooleanExpression commentAlive = USE.commentId.isNull().or(JPAExpressions.selectOne().from(comment)
                .where(comment.postId.eq(USE.postId), comment.id.eq(USE.commentId), comment.deletedAt.isNull()).exists());
        return query.select(USE.id).from(USE).where(postAlive.and(commentAlive).not()).orderBy(USE.id.asc()).limit(limit).fetch();
    }

    public long deleteUsesById(Collection<Long> ids) {
        return ids.isEmpty() ? 0 : query.delete(USE).where(USE.id.in(ids)).execute();
    }

    /**
     * 지울 짤 파일 후보(오래된 순) — 보이는(ACTIVE) 파일 중 {@code createdBefore} 전에 만들었고 가리키는 보관함 줄도 쓰임도 없는 것.
     * 내려간(BLOCKED) 파일은 지우지 않는다 — 같은 해시 다시 올리기를 거절하려면 해시가 남아야 한다.
     */
    public List<Long> orphanFiles(Instant createdBefore, int limit) {
        return query.select(FILE.id).from(FILE)
                .where(FILE.status.eq(JjalFileStatus.ACTIVE), FILE.createdAt.lt(createdBefore), unreferenced())
                .orderBy(FILE.createdAt.asc(), FILE.id.asc())
                .limit(limit)
                .fetch();
    }

    /**
     * 저장 위치가 DB 상태와 어긋났을 수 있는 파일(ID 순, {@code afterId} 다음부터) — 배치 {@code jjal-storage-sync} 의 후보.
     * 내려갔는데 공개 위치에 있음 · 불법촬영물로 확정했는데 파일이 남음 · 비공개 위치에 있는데 보이는 상태(ACTIVE 또는 임시조치 기한 지남) · 임시조치 기한이 지난 BLOCKED.
     * 판정은 {@code JjalStorageSync#sync} 가 행을 다시 읽어 한다.
     */
    public List<Long> storageOutOfSync(Instant now, long afterId, int limit) {
        BooleanExpression privateKey = FILE.storageKey.startsWith(JjalFileEntity.PRIVATE_PREFIX);
        BooleanExpression blockedNow = FILE.status.eq(JjalFileStatus.BLOCKED).and(FILE.blockedUntil.isNull().or(FILE.blockedUntil.gt(now)));
        BooleanExpression expired = FILE.status.eq(JjalFileStatus.BLOCKED).and(FILE.blockedUntil.loe(now));
        BooleanExpression purgedLeft = FILE.purgedAt.isNotNull().and(FILE.storageKey.isNotNull());
        BooleanExpression blockedPublic = blockedNow.and(FILE.purgedAt.isNull()).and(FILE.storageKey.isNotNull()).and(privateKey.not());
        BooleanExpression visiblePrivate = privateKey.and(FILE.purgedAt.isNull()).and(FILE.status.eq(JjalFileStatus.ACTIVE).or(expired));
        return query.select(FILE.id).from(FILE)
                .where(FILE.id.gt(afterId), purgedLeft.or(blockedPublic).or(visiblePrivate).or(expired))
                .orderBy(FILE.id.asc())
                .limit(limit)
                .fetch();
    }

    /** 이 파일을 가리키는 보관함 줄·쓰임이 있는가(잠근 뒤 다시 본다) */
    public boolean referenced(long fileId) {
        return query.selectOne().from(FILE).where(FILE.id.eq(fileId), unreferenced()).fetchFirst() == null;
    }

    private static BooleanExpression unreferenced() {
        return JPAExpressions.selectOne().from(ITEM).where(ITEM.fileId.eq(FILE.id)).notExists()
                .and(JPAExpressions.selectOne().from(USE).where(USE.fileId.eq(FILE.id)).notExists());
    }
}
