package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.DealOgImageEntity;
import com.community.hotdeal.entity.QDealOgImageEntity;
import com.community.hotdeal.entity.QPostDealEntity;
import com.community.hotdeal.entity.QPostDealRevisionEntity;
import com.community.post.entity.QPostEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 핫딜 정리 배치({@code hotdeal-cleanup} — {@code DealCleanupService})의 질의 — OG 사본 고르기·지움 표시·딜 이미지 칸 비우기, 오래된 수정 이력 지우기. <b>트랜잭션은 부르는 서비스가 연다</b>(덩어리마다).
 */
@Repository
public class DealCleanupRepositoryQDSL extends IrobitRepositoryQDSL {

    private static final QDealOgImageEntity OG = QDealOgImageEntity.dealOgImageEntity;
    private static final QPostDealEntity D = QPostDealEntity.postDealEntity;
    private static final QPostEntity P = QPostEntity.postEntity;
    private static final QPostDealRevisionEntity REVISION = QPostDealRevisionEntity.postDealRevisionEntity;

    /** 잠긴 줄은 기다리지 않고 건너뛴다({@code SKIP LOCKED}) */
    private static final String LOCK_TIMEOUT = "jakarta.persistence.lock.timeout";
    private static final int SKIP_LOCKED = -2;

    public DealCleanupRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 지킬 딜이 없는 OG 사본을 <b>잠가</b> 고른다(id 차례, {@code FOR NO KEY UPDATE SKIP LOCKED} — 다른 실행·글 저장이 잡은 줄은 건너뛴다). 지키는 딜 = 글이 남아 있고 진행 중(ACTIVE)이거나
     * {@code keepFrom} 뒤에 끝남 · 또는 글을 {@code keepFrom} 뒤에 지움. {@code orphanBefore} 전에 만든 사본만.
     */
    public List<DealOgImageEntity> lockRemovableOgImages(Instant orphanBefore, Instant keepFrom, int limit) {
        var guarded = JPAExpressions.selectOne().from(D).join(P).on(P.id.eq(D.postId))
                .where(D.ogImageId.eq(OG.id), P.deletedAt.isNull().and(D.status.eq("ACTIVE").or(D.endedAt.gt(keepFrom))).or(P.deletedAt.gt(keepFrom)));
        // 잠금 세기: PESSIMISTIC_WRITE = FOR NO KEY UPDATE SKIP LOCKED(전환 전 FOR UPDATE SKIP LOCKED). deal_og_images 를 FK 로 가리키는 표가 없어(post_deals.og_image_id 는
        // FK 가 아니다 — V122, KEY SHARE 를 잡는 쪽이 없다) 건너뛰는 줄·막히는 쪽이 전환 전과 같다 — 그대로 둔다
        return query.selectFrom(OG).where(OG.removedAt.isNull(), OG.createdAt.lt(orphanBefore), guarded.notExists())
                .orderBy(OG.id.asc()).limit(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).setHint(LOCK_TIMEOUT, SKIP_LOCKED)
                .fetch();
    }

    /** 고른(잠근) 사본에 지움 표시 */
    public long markOgImagesRemoved(Collection<Long> ids, Instant now) {
        return query.update(OG).set(OG.removedAt, now).where(OG.id.in(ids), OG.removedAt.isNull()).execute();
    }

    /** 그 사본을 가리키던 딜의 이미지 칸을 비운다(없는 주소를 보이지 않게) */
    public long clearDealImages(Collection<Long> ogImageIds, Instant now) {
        return query.update(D).setNull(D.imageSource).setNull(D.ogImageId).setNull(D.imageUrl).setNull(D.imageId).set(D.updatedAt, now)
                .where(D.ogImageId.in(ogImageIds)).execute();
    }

    /** {@code before} 전에 만든 수정 이력 중 오래된 차례로 {@code limit} 줄의 id */
    public List<Long> oldRevisionIds(Instant before, int limit) {
        return query.select(REVISION.id).from(REVISION).where(REVISION.createdAt.lt(before)).orderBy(REVISION.createdAt.asc()).limit(limit).fetch();
    }

    public long deleteRevisions(Collection<Long> ids) {
        return query.delete(REVISION).where(REVISION.id.in(ids)).execute();
    }
}
