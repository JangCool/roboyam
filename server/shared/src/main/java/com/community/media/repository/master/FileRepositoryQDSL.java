package com.community.media.repository.master;

import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.entity.FileStatus;
import com.community.media.entity.QFileEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 글 이미지({@code files.purpose = POST_IMAGE}) 질의 — 글 저장 때 본문의 이미지 확인·붙이기, 고치기 때 빠진 이미지 찾기, 하루 올리기 수, 정리 배치의 「주인 잃은 이미지」.
 */
@Repository
public class FileRepositoryQDSL extends IrobitRepositoryQDSL {

    private static final QFileEntity FILE = QFileEntity.fileEntity;

    public FileRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 글 이미지들(잠그지 않음) — 저장 전 확인용 */
    public List<FileEntity> postImages(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return query.selectFrom(FILE).where(FILE.id.in(ids), FILE.purpose.eq(FilePurpose.POST_IMAGE)).fetch();
    }

    /**
     * 글 이미지들을 쓰기 잠금으로 읽는다(id 순 — 교착을 피한다). 글 저장 트랜잭션 안에서 부르며, 정리 배치도 같은 행을 잠그고 상태를 다시 보므로
     * 붙이는 중인 이미지를 배치가 지우지 못한다.
     */
    public List<FileEntity> lockPostImages(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return query.selectFrom(FILE).where(FILE.id.in(ids), FILE.purpose.eq(FilePurpose.POST_IMAGE)).orderBy(FILE.id.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
    }

    /** 이 글이 쓰는 이미지의 ID(잠그지 않음) */
    public List<Long> idsByPost(long postId) {
        return query.select(FILE.id).from(FILE).where(FILE.postId.eq(postId), FILE.purpose.eq(FilePurpose.POST_IMAGE)).orderBy(FILE.id.asc()).fetch();
    }

    /**
     * 글마다 쓰는 첫 이미지(id 가 가장 작은 것 = 먼저 올린 것)의 목록용 키 = {@code coalesce(thumbnail_key, storage_key)}(V206 — 썸네일이 없으면 원본) — 목록 썸네일.
     * 이미지가 없는 글은 빠진다
     */
    public Map<Long, String> firstImageKeys(Collection<Long> postIds) {
        Map<Long, String> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        query.select(FILE.postId, FILE.thumbnailKey.coalesce(FILE.storageKey)).from(FILE)
                .where(FILE.postId.in(postIds), FILE.purpose.eq(FilePurpose.POST_IMAGE), FILE.status.eq(FileStatus.IN_USE))
                .orderBy(FILE.id.asc()).fetch()
                .forEach(t -> out.putIfAbsent(t.get(0, Long.class), t.get(1, String.class)));
        return out;
    }

    /** 이 글이 쓰는 본문 이미지(IN_USE, 먼저 올린 차례) — 관리자 대표 썸네일 고르기 격자·자동 그림. 잠그지 않음 */
    public List<FileEntity> usedPostImages(long postId) {
        return query.selectFrom(FILE).where(FILE.postId.eq(postId), FILE.purpose.eq(FilePurpose.POST_IMAGE), FILE.status.eq(FileStatus.IN_USE))
                .orderBy(FILE.id.asc()).fetch();
    }

    /** 파일들(용도 무관, 잠그지 않음) — 대표 썸네일이 가리키는 파일을 한 번에 읽는다 */
    public List<FileEntity> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return query.selectFrom(FILE).where(FILE.id.in(ids)).fetch();
    }

    /**
     * 목록 썸네일이 없는 쓰는 중 글 이미지·대표 썸네일 그림의 ID(id 순, {@code afterId} 다음부터 {@code limit} 개) — 「글 이미지 썸네일 만들기」 배치(V206 부분 색인
     * {@code ix_files_thumbnail_missing}). 만들지 못한 파일은 다음 실행에서 다시 집힌다(같은 실행 안에서는 키셋이라 한 번만)
     */
    public List<Long> missingThumbnails(long afterId, int limit) {
        return query.select(FILE.id).from(FILE)
                .where(FILE.thumbnailKey.isNull(), FILE.status.eq(FileStatus.IN_USE), FILE.purpose.in(FilePurpose.POST_IMAGE, FilePurpose.POST_THUMBNAIL),
                        FILE.id.gt(afterId))
                .orderBy(FILE.id.asc())
                .limit(limit)
                .fetch();
    }

    /** 이 글이 쓰는 이미지(쓰기 잠금) */
    public List<FileEntity> lockByPost(long postId) {
        return query.selectFrom(FILE).where(FILE.postId.eq(postId), FILE.purpose.eq(FilePurpose.POST_IMAGE)).orderBy(FILE.id.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
    }

    /** 이 회원이 {@code since} 뒤에 올린 글 이미지 수(쓰였든 아니든, 대표 썸네일 그림 포함 — 하루 한도를 함께 쓴다, V206) */
    public long uploadsSince(long userId, Instant since) {
        Long count = query.select(FILE.id.count()).from(FILE)
                .where(FILE.uploadedByUser.eq(userId), FILE.createdAt.goe(since), FILE.purpose.in(FilePurpose.POST_IMAGE, FilePurpose.POST_THUMBNAIL))
                .fetchOne();
        return count == null ? 0 : count;
    }

    /**
     * 올리기만 하고 {@code before} 전까지 어디에도 쓰이지 않은 파일의 ID(오래된 순) — 정리 배치가 행을 잠가 다시 읽고 지운다(엔티티를 먼저 올리지 않는다:
     * 같은 트랜잭션의 잠금 조회가 영속성 컨텍스트의 옛 상태를 돌려주지 않게).
     */
    public List<Long> pendingBefore(Instant before, int limit) {
        return query.select(FILE.id).from(FILE)
                .where(FILE.status.eq(FileStatus.PENDING), FILE.createdAt.lt(before))
                .orderBy(FILE.createdAt.asc(), FILE.id.asc())
                .limit(limit)
                .fetch();
    }

    /**
     * 쓰는 중(IN_USE)인데 쓰던 글이 더는 보이지 않는 글 이미지 — 글을 지웠거나(바로·예약 삭제 시각 지남), 글 행이 없어졌다(게시판 삭제·개인정보 파기).
     * 정리 배치가 오래된 순(id)으로 {@code limit} 개씩 지운다. 신고로 숨긴 글의 이미지는 남는다. 대표 썸네일로 올린 그림(V206 {@code POST_THUMBNAIL})도 같다.
     */
    public List<Long> abandonedPostImages(Instant now, int limit) {
        QPostEntity post = QPostEntity.postEntity;
        return query.select(FILE.id).from(FILE)
                .where(FILE.purpose.in(FilePurpose.POST_IMAGE, FilePurpose.POST_THUMBNAIL), FILE.status.eq(FileStatus.IN_USE), FILE.postId.isNotNull(),
                        JPAExpressions.selectOne().from(post).where(post.id.eq(FILE.postId), PostRepositoryQDSL.alive(post, now)).notExists())
                .orderBy(FILE.id.asc())
                .limit(limit)
                .fetch();
    }
}
