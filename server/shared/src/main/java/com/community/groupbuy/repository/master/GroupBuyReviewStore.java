package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyParticipationEntity;
import com.community.groupbuy.entity.GroupBuyReviewEntity;
import com.community.groupbuy.entity.GroupBuyReviewPhotoEntity;
import com.community.groupbuy.entity.GroupBuyReviewReplyEntity;
import com.community.groupbuy.entity.GroupBuyReviewRevisionEntity;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationItemEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuyReviewEntity;
import com.community.groupbuy.entity.QGroupBuyReviewPhotoEntity;
import com.community.groupbuy.entity.QGroupBuyReviewReplyEntity;
import com.community.groupbuy.entity.QGroupBuyReviewRevisionEntity;
import com.community.groupbuy.entity.QGroupBuyShipmentEntity;
import com.community.groupbuy.entity.QGroupBuySkuEntity;
import com.community.groupbuy.entity.QGroupBuyUgcReportEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.zconfiguration.jpa.RowLocks;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 공구 리뷰({@code group_buy_reviews}·{@code group_buy_review_photos}·{@code group_buy_review_revisions}·{@code group_buy_review_replies}, 2b-6) —
 * {@code GroupBuyReviewService} 안에 있던 SQL 을 옮겼다. 모두 QueryDSL 이다({@link GroupBuyStoreSupport}).
 * 행 잠금은 {@link RowLocks}({@code FOR UPDATE} — 전환 전 세기, 조건으로 고른 뒤 잠그고 조건을 넣어 다시 읽는다) — 리뷰 쓰기는 주문 줄을, 고치기·답글·숨김은 리뷰 줄을 잠근다.
 */
@Repository
public class GroupBuyReviewStore extends GroupBuyStoreSupport {

    private static final QGroupBuyReviewEntity R = QGroupBuyReviewEntity.groupBuyReviewEntity;
    private static final QGroupBuyReviewPhotoEntity PH = QGroupBuyReviewPhotoEntity.groupBuyReviewPhotoEntity;
    private static final QGroupBuyReviewRevisionEntity REV = QGroupBuyReviewRevisionEntity.groupBuyReviewRevisionEntity;
    private static final QGroupBuyReviewReplyEntity RP = QGroupBuyReviewReplyEntity.groupBuyReviewReplyEntity;
    private static final QGroupBuyUgcReportEntity X = QGroupBuyUgcReportEntity.groupBuyUgcReportEntity;
    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QGroupBuyParticipationEntity PA = QGroupBuyParticipationEntity.groupBuyParticipationEntity;
    private static final QGroupBuyParticipationItemEntity I = QGroupBuyParticipationItemEntity.groupBuyParticipationItemEntity;
    private static final QGroupBuySkuEntity S = QGroupBuySkuEntity.groupBuySkuEntity;
    private static final QGroupBuyShipmentEntity SH = QGroupBuyShipmentEntity.groupBuyShipmentEntity;
    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity PR = QProfileEntity.profileEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QOperatorEntity O2 = new QOperatorEntity("updatedByOperator");

    /** 회원이 보는 리뷰 한 줄 — {@code nickname} 은 탈퇴·삭제한 회원이면 null, {@code title} 공구 제목(비었으면 상품 이름) */
    public record ReviewRow(long id, long userId, String nickname, int rating, String body, String skuName, int roundNo, long groupBuyId, String title, String orderNo,
                            Instant createdAt, Instant updatedAt, String hiddenCode, String hiddenMemo, String reply, Instant repliedAt, boolean replyEdited) {
    }

    /** 회원 목록 차례 */
    public enum Sort {
        LATEST, RATING_HIGH, RATING_LOW, PHOTO
    }

    /** 리뷰 사진 한 장 */
    public record PhotoRow(long reviewId, long id, String imageKey) {
    }

    /** 내 주문 줄의 리뷰 상태 재료 — {@code live} 남은 수량, {@code reviewId} 지우지 않은 리뷰 */
    public record TargetItem(long itemId, int live, ParticipationStatus status, Instant deliveredAt, Long reviewId) {
    }

    /** 리뷰를 쓸 주문 줄 */
    public record WritableLine(long participationId, long groupBuyId, long productId, String skuName, int live, ParticipationStatus status, Instant deliveredAt) {
    }

    /** 고치기 전 리뷰 */
    public record CurrentReview(int rating, String body, Instant createdAt, Instant updatedAt) {
    }

    /** 잠근 리뷰 */
    public record LockedReview(long userId, long groupBuyId, boolean deleted) {
    }

    /** 운영자가 보는 리뷰 한 줄 */
    public record AdminReviewRow(long id, long productId, String productName, long groupBuyId, String title, int roundNo, long participationId, String orderNo, String skuName,
                                 long userId, String nickname, boolean gone, int rating, String body, int photoCount, boolean hidden, boolean deleted, int reportCount,
                                 boolean hasReply, Instant createdAt, Instant updatedAt) {
    }

    /** 관리자 목록 조건 — {@code tab} VISIBLE·REPORTED·HIDDEN·ALL, {@code like} ILIKE 말(없으면 null), {@code number} 회원 번호(숫자 찾기 말일 때) */
    public record AdminFilter(String tab, Integer rating, boolean photo, boolean noReply, String like, Long number) {
    }

    public record ReviewExtra(Instant deletedAt, String code, String memo, Instant hiddenAt, Long hiddenBy, String hiddenByName) {
    }

    public record RevisionRow(long id, int rating, String body, int photoCount, Instant writtenAt, Instant replacedAt) {
    }

    public record ReplyDetail(String body, Instant repliedAt, Long repliedBy, String repliedByName, Instant updatedAt, Long updatedBy, String updatedByName) {
    }

    public GroupBuyReviewStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    // ── 회원: 보기 ──────────────────────────────────────────────────────────

    private static BooleanExpression hasPhoto() {
        return JPAExpressions.selectOne().from(PH).where(PH.reviewId.eq(R.id)).exists();
    }

    private JPAQuery<Tuple> memberSelect() {
        return query.select(R.id, R.userId, PR.nickname, U.id, U.status, U.deletedAt, R.rating, R.body, R.skuName, G.roundNo, R.groupBuyId, G.title, P.name, PA.orderNo,
                        R.createdAt, R.updatedAt, R.hiddenReasonCode, R.hiddenMemo, RP.body, RP.repliedAt, RP.updatedAt)
                .from(R).join(G).on(G.id.eq(R.groupBuyId)).join(P).on(P.id.eq(R.productId)).join(PA).on(PA.id.eq(R.participationId)).leftJoin(U).on(U.id.eq(R.userId))
                .leftJoin(PR).on(PR.userId.eq(R.userId)).leftJoin(RP).on(RP.reviewId.eq(R.id));
    }

    private static ReviewRow memberRow(Tuple t) {
        boolean gone = GroupBuyRowNames.gone(t.get(U.id), t.get(U.status), t.get(U.deletedAt));
        return new ReviewRow(t.get(R.id), t.get(R.userId), gone ? null : t.get(PR.nickname), n(t.get(R.rating)), t.get(R.body), t.get(R.skuName), n(t.get(G.roundNo)),
                t.get(R.groupBuyId), GroupBuyRowNames.title(t.get(G.title), t.get(P.name)), t.get(PA.orderNo), t.get(R.createdAt), t.get(R.updatedAt),
                t.get(R.hiddenReasonCode), t.get(R.hiddenMemo), t.get(RP.body), t.get(RP.repliedAt), t.get(RP.updatedAt) != null);
    }

    private static BooleanExpression productWhere(long productId, long viewer, boolean photoOnly) {
        BooleanExpression where = R.productId.eq(productId).and(R.deletedAt.isNull()).and(R.hiddenAt.isNull().or(R.userId.eq(viewer)));
        return photoOnly ? where.and(hasPhoto()) : where;
    }

    /** 상품의 리뷰 수 — 숨긴 것은 작성자({@code viewer}, 로그인 안 했으면 -1)에게만 */
    public long productCount(long productId, long viewer, boolean photoOnly) {
        return n(query.select(R.count()).from(R).where(productWhere(productId, viewer, photoOnly)).fetchOne());
    }

    public List<ReviewRow> productPage(long productId, long viewer, boolean photoOnly, Sort sort, long offset, int limit) {
        List<OrderSpecifier<?>> order = new ArrayList<>();
        switch (sort) {
            case RATING_HIGH -> order.add(R.rating.desc());
            case RATING_LOW -> order.add(R.rating.asc());
            case PHOTO -> order.add(new CaseBuilder().when(hasPhoto()).then(1).otherwise(0).desc()); // 예전 ORDER BY EXISTS(…) DESC — 사진 있는 것 먼저
            case LATEST -> {
            }
        }
        order.add(R.createdAt.desc());
        order.add(R.id.desc());
        return memberSelect().where(productWhere(productId, viewer, photoOnly)).orderBy(order.toArray(OrderSpecifier[]::new)).offset(offset).limit(limit).fetch().stream()
                .map(GroupBuyReviewStore::memberRow).toList();
    }

    public Optional<ReviewRow> memberRow(long id) {
        return Optional.ofNullable(memberSelect().where(R.id.eq(id)).fetchFirst()).map(GroupBuyReviewStore::memberRow);
    }

    /** 내 리뷰 수(지운 것 빼고) */
    public long mineCount(long userId) {
        return n(query.select(R.count()).from(R).where(R.userId.eq(userId), R.deletedAt.isNull()).fetchOne());
    }

    /** 내 리뷰 한 쪽 — 새 것 먼저 */
    public List<ReviewRow> minePage(long userId, long offset, int limit) {
        return memberSelect().where(R.userId.eq(userId), R.deletedAt.isNull()).orderBy(R.createdAt.desc(), R.id.desc()).offset(offset).limit(limit).fetch().stream()
                .map(GroupBuyReviewStore::memberRow).toList();
    }

    /** 공개 리뷰(숨기지·지우지 않음)의 별점별 수 */
    public Map<Integer, Long> ratingCounts(long productId) {
        Map<Integer, Long> out = new HashMap<>();
        query.select(R.rating, R.count()).from(R).where(R.productId.eq(productId), R.deletedAt.isNull(), R.hiddenAt.isNull()).groupBy(R.rating).fetch()
                .forEach(t -> out.put(t.get(0, Integer.class), n(t.get(1, Long.class))));
        return out;
    }

    /** 사진이 있는 공개 리뷰 수 */
    public long photoReviewCount(long productId) {
        return n(query.select(R.count()).from(R).where(R.productId.eq(productId), R.deletedAt.isNull(), R.hiddenAt.isNull(), hasPhoto()).fetchOne());
    }

    /** 사진 모아보기 — 공개 리뷰의 사진, 리뷰 새 것 먼저 · 리뷰 안의 차례 */
    public List<PhotoRow> photoStrip(long productId, int limit) {
        return query.select(PH.reviewId, PH.id, PH.imageKey).from(PH).join(R).on(R.id.eq(PH.reviewId))
                .where(R.productId.eq(productId), R.deletedAt.isNull(), R.hiddenAt.isNull()).orderBy(R.createdAt.desc(), PH.position.asc()).limit(limit).fetch().stream()
                .map(t -> new PhotoRow(t.get(PH.reviewId), t.get(PH.id), t.get(PH.imageKey))).toList();
    }

    /** 상품마다 공개 리뷰의 별점 평균·수 — (평균, 수) */
    public Map<Long, Object[]> stats(Collection<Long> productIds) {
        Map<Long, Object[]> out = new HashMap<>();
        query.select(R.productId, R.rating.avg(), R.count()).from(R).where(R.productId.in(productIds), R.deletedAt.isNull(), R.hiddenAt.isNull()).groupBy(R.productId)
                .fetch().forEach(t -> out.put(t.get(0, Long.class), new Object[] {t.get(1, Double.class), n(t.get(2, Long.class))}));
        return out;
    }

    /** 주문의 줄마다 리뷰 상태 재료 — 줄 차례 */
    public List<TargetItem> targetItems(String orderNo, long userId) {
        return query.select(I.id, live(), PA.status, SH.deliveredAt, R.id).from(PA).join(I).on(I.participationId.eq(PA.id))
                .leftJoin(SH).on(SH.participationId.eq(PA.id), SH.kind.eq("ORIGINAL")).leftJoin(R).on(R.itemId.eq(I.id), R.deletedAt.isNull())
                .where(PA.orderNo.eq(orderNo), PA.userId.eq(userId)).orderBy(I.id.asc()).fetch().stream()
                .map(t -> new TargetItem(t.get(I.id), n(t.get(live())), t.get(PA.status), t.get(SH.deliveredAt), t.get(R.id))).toList();
    }

    private static NumberExpression<Integer> live() {
        return I.qty.subtract(I.canceledQty).subtract(I.returnedQty);
    }

    /** 그 주문에서 지운 리뷰가 있는 줄 */
    public List<Long> deletedItems(String orderNo, long userId) {
        return query.select(R.itemId).from(R).join(PA).on(PA.id.eq(R.participationId)).where(PA.orderNo.eq(orderNo), PA.userId.eq(userId), R.deletedAt.isNotNull()).fetch();
    }

    // ── 회원: 쓰기 ──────────────────────────────────────────────────────────

    public void insertPhoto(long id, long userId, String imageKey, Instant now) {
        insert(GroupBuyReviewPhotoEntity.loose(id, userId, imageKey, now));
    }

    /** 그 회원 주문의 줄 — 주문 줄을 먼저 잠그고(예전 {@code FOR UPDATE OF pa}) 읽는다 */
    public Optional<WritableLine> lockWritableLine(long itemId, String orderNo, long userId) {
        Long participationId = query.select(PA.id).from(PA)
                .where(PA.orderNo.eq(orderNo), PA.userId.eq(userId), JPAExpressions.selectOne().from(I).where(I.id.eq(itemId), I.participationId.eq(PA.id)).exists())
                .fetchFirst();
        if (participationId == null || !RowLocks.forUpdate(entityManager, GroupBuyParticipationEntity.class, participationId)) {
            return Optional.empty();
        }
        Tuple t = query.select(PA.id, PA.groupBuyId, G.productId, S.name, live(), PA.status, SH.deliveredAt).from(I).join(PA).on(PA.id.eq(I.participationId))
                .join(G).on(G.id.eq(PA.groupBuyId)).join(S).on(S.id.eq(I.skuId)).leftJoin(SH).on(SH.participationId.eq(PA.id), SH.kind.eq("ORIGINAL"))
                .where(I.id.eq(itemId), PA.id.eq(participationId), PA.orderNo.eq(orderNo), PA.userId.eq(userId)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new WritableLine(t.get(PA.id), t.get(PA.groupBuyId), t.get(G.productId), t.get(S.name), n(t.get(live())),
                t.get(PA.status), t.get(SH.deliveredAt)));
    }

    /** 리뷰를 넣는다 — 같은 줄에 이미 있으면 {@link org.springframework.dao.DuplicateKeyException}({@code ux_gbr_item}) */
    public void insertReview(long id, long productId, long groupBuyId, long participationId, long itemId, long userId, String skuName, int rating, String body,
                             Instant now) {
        insert(GroupBuyReviewEntity.written(id, productId, groupBuyId, participationId, itemId, userId, skuName, rating, body, now));
    }

    /** 내 지우지 않은 리뷰를 잠그고 읽는다 */
    public Optional<CurrentReview> lockOwn(long id, long userId) {
        Long own = query.select(R.id).from(R).where(R.id.eq(id), R.userId.eq(userId), R.deletedAt.isNull()).fetchFirst();
        if (own == null || !RowLocks.forUpdate(entityManager, GroupBuyReviewEntity.class, own)) {
            return Optional.empty();
        }
        Tuple t = query.select(R.rating, R.body, R.createdAt, R.updatedAt).from(R).where(R.id.eq(id), R.userId.eq(userId), R.deletedAt.isNull()).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new CurrentReview(n(t.get(R.rating)), t.get(R.body), t.get(R.createdAt), t.get(R.updatedAt)));
    }

    public int photoCount(long reviewId) {
        return (int) n(query.select(PH.count()).from(PH).where(PH.reviewId.eq(reviewId)).fetchOne());
    }

    public void insertRevision(long id, long reviewId, int rating, String body, int photoCount, Instant writtenAt, Instant replacedAt) {
        insert(GroupBuyReviewRevisionEntity.of(id, reviewId, rating, body, photoCount, writtenAt, replacedAt));
    }

    public void updateReview(long id, int rating, String body, Instant now) {
        query.update(R).set(R.rating, rating).set(R.body, body).set(R.updatedAt, now).where(R.id.eq(id)).execute();
    }

    /** 리뷰의 사진 중 {@code keep} 에 없는 것을 지운다 */
    public void deletePhotosExcept(long reviewId, List<Long> keep) {
        BooleanExpression where = PH.reviewId.eq(reviewId);
        query.delete(PH).where(keep.isEmpty() ? where : where.and(PH.id.notIn(keep))).execute();
    }

    /** 내가 올린 사진을 리뷰에 붙인다(차례 {@code position}) — 남의 사진·다른 리뷰의 사진이면 false */
    public boolean attachPhoto(long photoId, long userId, long reviewId, int position, Instant now) {
        return query.update(PH).set(PH.reviewId, reviewId).set(PH.attachedAt, PH.attachedAt.coalesce(now)).set(PH.position, position)
                .where(PH.id.eq(photoId), PH.userId.eq(userId), PH.reviewId.isNull().or(PH.reviewId.eq(reviewId))).execute() > 0;
    }

    /** 내 리뷰를 지운다(줄은 남는다) — 없으면 false */
    public boolean softDelete(long id, long userId, Instant now) {
        return query.update(R).set(R.deletedAt, now).where(R.id.eq(id), R.userId.eq(userId), R.deletedAt.isNull()).execute() > 0;
    }

    /** 리뷰마다 사진 — 리뷰 · 차례 · ID 순 */
    public List<PhotoRow> photosOf(Collection<Long> reviewIds) {
        return query.select(PH.reviewId, PH.id, PH.imageKey).from(PH).where(PH.reviewId.in(reviewIds)).orderBy(PH.reviewId.asc(), PH.position.asc(), PH.id.asc()).fetch()
                .stream().map(t -> new PhotoRow(t.get(PH.reviewId), t.get(PH.id), t.get(PH.imageKey))).toList();
    }

    // ── 운영자 ──────────────────────────────────────────────────────────────

    private static BooleanExpression reported() {
        return JPAExpressions.selectOne().from(X).where(X.targetType.eq("GROUP_BUY_REVIEW"), X.targetId.eq(R.id), X.status.eq("OPEN")).exists();
    }

    private static Expression<Long> photoCountOf() {
        return JPAExpressions.select(PH.count()).from(PH).where(PH.reviewId.eq(R.id));
    }

    private static Expression<Long> reportCountOf() {
        return JPAExpressions.select(X.count()).from(X).where(X.targetType.eq("GROUP_BUY_REVIEW"), X.targetId.eq(R.id), X.status.eq("OPEN"));
    }

    private JPAQuery<?> adminFrom(JPAQuery<?> q) {
        return q.from(R).join(G).on(G.id.eq(R.groupBuyId)).join(P).on(P.id.eq(R.productId)).join(PA).on(PA.id.eq(R.participationId));
    }

    private JPAQuery<Tuple> adminSelect() {
        JPAQuery<Tuple> q = query.select(R.id, R.productId, P.name, R.groupBuyId, G.title, G.roundNo, R.participationId, PA.orderNo, R.skuName, R.userId, PR.nickname, U.id,
                U.status, U.deletedAt, R.rating, R.body, photoCountOf(), R.hiddenAt, R.deletedAt, reportCountOf(), RP.reviewId, R.createdAt, R.updatedAt);
        adminFrom(q).leftJoin(U).on(U.id.eq(R.userId)).leftJoin(PR).on(PR.userId.eq(R.userId)).leftJoin(RP).on(RP.reviewId.eq(R.id));
        return q;
    }

    private static AdminReviewRow adminRow(Tuple t) {
        Long photos = t.get(16, Long.class);
        Long reports = t.get(19, Long.class);
        return new AdminReviewRow(t.get(R.id), t.get(R.productId), t.get(P.name), t.get(R.groupBuyId), GroupBuyRowNames.title(t.get(G.title), t.get(P.name)),
                n(t.get(G.roundNo)), t.get(R.participationId), t.get(PA.orderNo), t.get(R.skuName), t.get(R.userId), t.get(PR.nickname),
                GroupBuyRowNames.gone(t.get(U.id), t.get(U.status), t.get(U.deletedAt)), n(t.get(R.rating)), t.get(R.body), photos == null ? 0 : photos.intValue(),
                t.get(R.hiddenAt) != null, t.get(R.deletedAt) != null, reports == null ? 0 : reports.intValue(), t.get(RP.reviewId) != null, t.get(R.createdAt),
                t.get(R.updatedAt));
    }

    private static BooleanExpression adminWhere(AdminFilter f) {
        BooleanExpression where = switch (f.tab()) {
            case "VISIBLE" -> R.hiddenAt.isNull().and(R.deletedAt.isNull());
            case "REPORTED" -> R.hiddenAt.isNull().and(R.deletedAt.isNull()).and(reported());
            case "HIDDEN" -> R.hiddenAt.isNotNull().and(R.deletedAt.isNull());
            default -> null; // ALL
        };
        if (f.rating() != null) {
            where = and(where, R.rating.eq(f.rating()));
        }
        if (f.photo()) {
            where = and(where, hasPhoto());
        }
        if (f.noReply()) {
            where = and(where, RP.reviewId.isNull());
        }
        if (f.like() != null) {
            BooleanExpression match = GroupBuyRowNames.ilike(P.name, f.like()).or(GroupBuyRowNames.ilike(G.title, f.like())).or(GroupBuyRowNames.ilike(PR.nickname, f.like()))
                    .or(GroupBuyRowNames.ilike(PA.orderNo, f.like()));
            if (f.number() != null) {
                match = match.or(R.userId.eq(f.number()));
            }
            where = and(where, match);
        }
        return where;
    }

    private static BooleanExpression and(BooleanExpression where, BooleanExpression more) {
        return where == null ? more : where.and(more);
    }

    /** 관리자 목록의 수(같은 조건 · 같은 조인) */
    public long adminCount(AdminFilter f) {
        JPAQuery<Long> q = query.select(R.count());
        adminFrom(q).leftJoin(PR).on(PR.userId.eq(R.userId)).leftJoin(RP).on(RP.reviewId.eq(R.id)).where(adminWhere(f));
        return n(q.fetchOne());
    }

    /** 관리자 목록 한 쪽 — 새 것 먼저 */
    public List<AdminReviewRow> adminPage(AdminFilter f, long offset, int limit) {
        return adminSelect().where(adminWhere(f)).orderBy(R.createdAt.desc(), R.id.desc()).offset(offset).limit(limit).fetch().stream().map(GroupBuyReviewStore::adminRow)
                .toList();
    }

    public Optional<AdminReviewRow> adminRow(long id) {
        return Optional.ofNullable(adminSelect().where(R.id.eq(id)).fetchFirst()).map(GroupBuyReviewStore::adminRow);
    }

    /** 탭 VISIBLE · REPORTED · HIDDEN 의 수 */
    public long[] tabCounts() {
        long visible = n(query.select(R.count()).from(R).where(R.hiddenAt.isNull(), R.deletedAt.isNull()).fetchOne());
        long reported = n(query.select(R.count()).from(R).where(R.hiddenAt.isNull(), R.deletedAt.isNull(), reported()).fetchOne());
        long hidden = n(query.select(R.count()).from(R).where(R.hiddenAt.isNotNull(), R.deletedAt.isNull()).fetchOne());
        return new long[] {visible, reported, hidden};
    }

    public Optional<ReviewExtra> extra(long id) {
        Tuple t = query.select(R.deletedAt, R.hiddenReasonCode, R.hiddenMemo, R.hiddenAt, R.hiddenBy, O.operatorName).from(R).leftJoin(O).on(O.id.eq(R.hiddenBy))
                .where(R.id.eq(id)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new ReviewExtra(t.get(R.deletedAt), t.get(R.hiddenReasonCode), t.get(R.hiddenMemo), t.get(R.hiddenAt),
                t.get(R.hiddenBy), t.get(O.operatorName)));
    }

    /** 작성자 수정 이력 — 새 것 먼저 */
    public List<RevisionRow> revisions(long reviewId) {
        return query.select(REV.id, REV.rating, REV.body, REV.photoCount, REV.writtenAt, REV.replacedAt).from(REV).where(REV.reviewId.eq(reviewId))
                .orderBy(REV.replacedAt.desc(), REV.id.desc()).fetch().stream()
                .map(t -> new RevisionRow(t.get(REV.id), n(t.get(REV.rating)), t.get(REV.body), n(t.get(REV.photoCount)), t.get(REV.writtenAt), t.get(REV.replacedAt)))
                .toList();
    }

    public Optional<ReplyDetail> reply(long reviewId) {
        Tuple t = query.select(RP.body, RP.repliedAt, RP.repliedBy, O.operatorName, RP.updatedAt, RP.updatedBy, O2.operatorName).from(RP).leftJoin(O).on(O.id.eq(RP.repliedBy))
                .leftJoin(O2).on(O2.id.eq(RP.updatedBy)).where(RP.reviewId.eq(reviewId)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new ReplyDetail(t.get(RP.body), t.get(RP.repliedAt), t.get(RP.repliedBy), t.get(O.operatorName),
                t.get(RP.updatedAt), t.get(RP.updatedBy), t.get(O2.operatorName)));
    }

    /** 리뷰 줄을 잠그고(답글·숨김끼리 줄 세움) 읽는다 */
    public Optional<LockedReview> lock(long id) {
        if (!RowLocks.forUpdate(entityManager, GroupBuyReviewEntity.class, id)) {
            return Optional.empty();
        }
        Tuple t = query.select(R.userId, R.groupBuyId, R.deletedAt).from(R).where(R.id.eq(id)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new LockedReview(t.get(R.userId), t.get(R.groupBuyId), t.get(R.deletedAt) != null));
    }

    public Optional<String> replyBody(long reviewId) {
        return Optional.ofNullable(query.select(RP.body).from(RP).where(RP.reviewId.eq(reviewId)).fetchFirst());
    }

    public void insertReply(long reviewId, String body, long operatorId, Instant now) {
        insert(GroupBuyReviewReplyEntity.first(reviewId, body, operatorId, now));
    }

    public void updateReply(long reviewId, String body, long operatorId, Instant now) {
        query.update(RP).set(RP.body, body).set(RP.updatedBy, operatorId).set(RP.updatedAt, now).where(RP.reviewId.eq(reviewId)).execute();
    }

    public void hide(long id, String reasonCode, String memo, long operatorId, Instant now) {
        query.update(R).set(R.hiddenReasonCode, reasonCode).set(R.hiddenMemo, memo).set(R.hiddenAt, now).set(R.hiddenBy, operatorId).where(R.id.eq(id)).execute();
    }

    public void unhide(long id) {
        query.update(R).setNull(R.hiddenReasonCode).setNull(R.hiddenMemo).setNull(R.hiddenAt).setNull(R.hiddenBy).where(R.id.eq(id)).execute();
    }
}
