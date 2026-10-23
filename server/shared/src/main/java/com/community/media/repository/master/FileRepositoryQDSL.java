package com.community.media.repository.master;

import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.entity.FileStatus;
import com.community.media.entity.QFileEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 글 이미지({@code files.purpose = POST_IMAGE}) 질의 — 글 저장 때 본문의 이미지 확인·붙이기, 고치기 때 빠진 이미지 찾기, 하루 올리기 수, 정리 배치의 「주인 잃은 이미지」.
 */
@Repository
public class FileRepositoryQDSL extends RoboyamRepositoryQDSL {

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

    /** 이 글이 쓰는 이미지(쓰기 잠금) */
    public List<FileEntity> lockByPost(long postId) {
        return query.selectFrom(FILE).where(FILE.postId.eq(postId), FILE.purpose.eq(FilePurpose.POST_IMAGE)).orderBy(FILE.id.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
    }

    /** 이 회원이 {@code since} 뒤에 올린 글 이미지 수(쓰였든 아니든 — 하루 한도) */
    public long uploadsSince(long userId, Instant since) {
        Long count = query.select(FILE.id.count()).from(FILE)
                .where(FILE.uploadedByUser.eq(userId), FILE.createdAt.goe(since), FILE.purpose.eq(FilePurpose.POST_IMAGE))
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
     * 정리 배치가 오래된 순(id)으로 {@code limit} 개씩 지운다. 신고로 숨긴 글의 이미지는 남는다.
     */
    public List<Long> abandonedPostImages(Instant now, int limit) {
        QPostEntity post = QPostEntity.postEntity;
        return query.select(FILE.id).from(FILE)
                .where(FILE.purpose.eq(FilePurpose.POST_IMAGE), FILE.status.eq(FileStatus.IN_USE), FILE.postId.isNotNull(),
                        JPAExpressions.selectOne().from(post).where(post.id.eq(FILE.postId), PostRepositoryQDSL.alive(post, now)).notExists())
                .orderBy(FILE.id.asc())
                .limit(limit)
                .fetch();
    }
}
