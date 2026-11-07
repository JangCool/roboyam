package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.QGroupBuyCategoryEntity;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuyProductImageEntity;
import com.community.groupbuy.entity.QGroupBuyProductSkuEntity;
import com.community.groupbuy.entity.QGroupBuySkuEntity;
import com.community.groupbuy.entity.SkuKind;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 관리자 공구 상품·카테고리 화면({@code GroupBuyProductAdminFacade})이 읽고 쓰는 값 — Facade 안에 있던 SQL 을 옮겼다. 모두 QueryDSL 이다(부르는 쪽 트랜잭션에 참여한다).
 * 예전 목록 줄의 「대표 이미지」 상관 부분 질의({@code ORDER BY sort_order LIMIT 1})는 그 쪽 상품들의 이미지를 한 번에 읽어 상품마다 첫 장을 고른다({@link #thumbKeys}).
 */
@Repository
public class GroupBuyProductAdminQueries extends RoboyamRepositoryQDSL {

    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QGroupBuyCategoryEntity C = QGroupBuyCategoryEntity.groupBuyCategoryEntity;
    private static final QGroupBuyProductSkuEntity PS = QGroupBuyProductSkuEntity.groupBuyProductSkuEntity;
    private static final QGroupBuyProductImageEntity IMG = QGroupBuyProductImageEntity.groupBuyProductImageEntity;
    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuySkuEntity S = QGroupBuySkuEntity.groupBuySkuEntity;

    public record ProductListRow(long id, String name, long categoryId, Instant updatedAt, int skuCount, int groupBuyCount, int runningCount) {
    }

    public record UseRow(long id, String title, GroupBuyStatus status, int roundNo) {
    }

    public record AddonSourceRow(long productSkuId, long productId, String productName, String skuName, int productSkuCount, Long defaultSupplyPrice) {
    }

    public GroupBuyProductAdminQueries(JPAQueryFactory query) {
        super(query);
    }

    // ── 카테고리 ────────────────────────────────────────────────────────────

    /** 카테고리마다 상품 수 */
    public Map<Long, Integer> productCounts() {
        Map<Long, Integer> out = new HashMap<>();
        query.select(P.categoryId, P.count()).from(P).groupBy(P.categoryId).fetch().forEach(t -> out.put(t.get(0, Long.class), (int) n(t.get(1, Long.class))));
        return out;
    }

    /** 부모를 바꾼다 — 이미 같은 부모면 그대로(예전 {@code parent_id IS DISTINCT FROM ?}) */
    public void moveCategory(long id, Long parentId) {
        BooleanExpression differs = parentId == null ? C.parentId.isNotNull() : C.parentId.isNull().or(C.parentId.ne(parentId));
        if (parentId == null) {
            query.update(C).setNull(C.parentId).where(C.id.eq(id), differs).execute();
        } else {
            query.update(C).set(C.parentId, parentId).where(C.id.eq(id), differs).execute();
        }
    }

    /** 지우려는 카테고리를 쓰는 곳 — 그 카테고리의 상품 수 + 남기지 않는 아래 단 수(남기는 것은 {@code keep}) */
    public int categoryUses(long id, Collection<Long> keep) {
        long products = n(query.select(P.count()).from(P).where(P.categoryId.eq(id)).fetchOne());
        BooleanExpression children = C.parentId.eq(id);
        long kids = n(query.select(C.count()).from(C).where(keep.isEmpty() ? children : children.and(C.id.notIn(keep))).fetchOne());
        return (int) (products + kids);
    }

    public void deleteCategory(long id) {
        query.delete(C).where(C.id.eq(id)).execute();
    }

    // ── 상품 ────────────────────────────────────────────────────────────────

    private static BooleanExpression listWhere(String like, Long categoryId) {
        BooleanExpression where = like == null ? null : GroupBuyRowNames.ilike(P.name, like);
        if (categoryId != null) {
            BooleanExpression inCategory = P.categoryId.eq(categoryId).or(P.categoryId.in(JPAExpressions.select(C.id).from(C).where(C.parentId.eq(categoryId))));
            where = where == null ? inCategory : where.and(inCategory);
        }
        return where;
    }

    /** 상품 목록의 수 — {@code like} 이름 ILIKE 말, {@code categoryId} 그 카테고리와 바로 아래 단(없으면 null) */
    public long productCount(String like, Long categoryId) {
        return n(query.select(P.count()).from(P).where(listWhere(like, categoryId)).fetchOne());
    }

    /** 상품 목록 한 쪽 — 최근에 고친 것 먼저 */
    public List<ProductListRow> productPage(String like, Long categoryId, long offset, int limit) {
        QGroupBuyProductSkuEntity k = new QGroupBuyProductSkuEntity("productSku");
        QGroupBuyEntity g = new QGroupBuyEntity("usingGroupBuy");
        QGroupBuyEntity running = new QGroupBuyEntity("runningGroupBuy");
        Expression<Long> skus = JPAExpressions.select(k.count()).from(k).where(k.productId.eq(P.id));
        Expression<Long> groupBuys = JPAExpressions.select(g.count()).from(g).where(g.productId.eq(P.id));
        Expression<Long> runningCount = JPAExpressions.select(running.count()).from(running)
                .where(running.productId.eq(P.id), running.status.in(GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING));
        return query.select(P.id, P.name, P.categoryId, P.updatedAt, skus, groupBuys, runningCount).from(P).where(listWhere(like, categoryId))
                .orderBy(P.updatedAt.desc(), P.id.desc()).offset(offset).limit(limit).fetch().stream()
                .map(t -> new ProductListRow(t.get(P.id), t.get(P.name), t.get(P.categoryId), t.get(P.updatedAt), (int) n(t.get(4, Long.class)), (int) n(t.get(5, Long.class)),
                        (int) n(t.get(6, Long.class)))).toList();
    }

    /**
     * 상품마다 대표 이미지 키 — 차례가 가장 앞인 이미지의 썸네일(없으면 원본). 예전 {@code (SELECT COALESCE(thumb_key, image_key) … ORDER BY sort_order LIMIT 1)}.
     * 이미지가 없는 상품은 빠진다.
     */
    public Map<Long, String> thumbKeys(Collection<Long> productIds) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        query.select(IMG.productId, IMG.thumbKey, IMG.imageKey).from(IMG).where(IMG.productId.in(productIds)).orderBy(IMG.productId.asc(), IMG.sortOrder.asc()).fetch()
                .forEach(t -> out.putIfAbsent(t.get(IMG.productId), t.get(IMG.thumbKey) != null ? t.get(IMG.thumbKey) : t.get(IMG.imageKey)));
        return out;
    }

    /** 상품을 쓰는 공구(본품 또는 추가 구성상품) — 새로 만든 것 먼저 */
    public List<UseRow> uses(long productId) {
        BooleanExpression addon = G.id.in(JPAExpressions.select(S.groupBuyId).from(S).join(PS).on(PS.id.eq(S.productSkuId))
                .where(PS.productId.eq(productId), S.kind.eq(SkuKind.ADDON)));
        return query.select(G.id, G.title, P.name, G.status, G.roundNo).from(G).join(P).on(P.id.eq(G.productId)).where(G.productId.eq(productId).or(addon))
                .orderBy(G.createdAt.desc()).fetch().stream()
                .map(t -> new UseRow(t.get(G.id), GroupBuyRowNames.title(t.get(G.title), t.get(P.name)), t.get(G.status), t.get(G.roundNo) == null ? 0 : t.get(G.roundNo)))
                .toList();
    }

    /** 공개된 진행 공구(DRAFT·RECRUITING·CLOSING)가 상품(본품 또는 어떤 단품이든)을 쓰는가 — 상품 잠금 */
    public boolean lockedByPublished(long productId) {
        BooleanExpression usesSku = G.id.in(JPAExpressions.select(S.groupBuyId).from(S).join(PS).on(PS.id.eq(S.productSkuId)).where(PS.productId.eq(productId)));
        return query.selectOne().from(G).where(G.publishedAt.isNotNull(), G.status.in(GroupBuyStatus.DRAFT, GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING),
                G.productId.eq(productId).or(usesSku)).fetchFirst() != null;
    }

    /** 추가 구성상품으로 고를 단품 — 상품 이름 · 단품 차례. {@code except} 상품은 뺀다(null 이면 모두) */
    public List<AddonSourceRow> addonSources(Long except) {
        QGroupBuyProductSkuEntity x = new QGroupBuyProductSkuEntity("siblingSku");
        Expression<Long> siblings = JPAExpressions.select(x.count()).from(x).where(x.productId.eq(P.id));
        return query.select(PS.id, P.id, P.name, PS.name, siblings, PS.defaultSupplyPrice).from(PS).join(P).on(P.id.eq(PS.productId))
                .where(except == null ? null : P.id.ne(except)).orderBy(P.name.asc(), PS.sortOrder.asc()).fetch().stream()
                .map(t -> new AddonSourceRow(t.get(PS.id), t.get(P.id), t.get(P.name), t.get(PS.name), (int) n(t.get(4, Long.class)), t.get(PS.defaultSupplyPrice))).toList();
    }

    static long n(Long value) {
        return value == null ? 0 : value;
    }
}
