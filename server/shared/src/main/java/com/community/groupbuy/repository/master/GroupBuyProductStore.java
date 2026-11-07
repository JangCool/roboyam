package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyCategoryEntity;
import com.community.groupbuy.entity.GroupBuyOptionAxisEntity;
import com.community.groupbuy.entity.GroupBuyOptionValueEntity;
import com.community.groupbuy.entity.GroupBuyProductEntity;
import com.community.groupbuy.entity.GroupBuyProductImageEntity;
import com.community.groupbuy.entity.GroupBuyProductSkuEntity;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.QGroupBuyCategoryEntity;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyOptionAxisEntity;
import com.community.groupbuy.entity.QGroupBuyOptionTypeEntity;
import com.community.groupbuy.entity.QGroupBuyOptionValueEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuyProductImageEntity;
import com.community.groupbuy.entity.QGroupBuyProductSkuEntity;
import com.community.groupbuy.entity.QGroupBuySkuEntity;
import com.community.groupbuy.entity.SkuKind;
import com.community.groupbuy.repository.master.GroupBuyStore.AxisRow;
import com.community.groupbuy.repository.master.GroupBuyStore.ImageRow;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 상품 마스터(결정 89) — 카테고리({@code group_buy_categories})·상품({@code group_buy_products})·상품 이미지·옵션 축/값·상품 단품 읽기·쓰기.
 *
 * <p>QueryDSL 이 기본이다({@link GroupBuyStoreSupport}). 상품 행 잠금은 상품 행만 고르는 잠금 문장 뒤에 읽는다.
 * Native SQL 로 남긴 것과 이유: 옵션 종류 넣기·고치기({@code INSERT … ON CONFLICT (code) DO UPDATE} — 코드가 키인 upsert), 상품 행 잠금의 {@code FOR UPDATE}({@link #lock}).
 */
@Repository
public class GroupBuyProductStore extends GroupBuyStoreSupport {

    private static final QGroupBuyCategoryEntity C = QGroupBuyCategoryEntity.groupBuyCategoryEntity;
    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QGroupBuyProductImageEntity IMG = QGroupBuyProductImageEntity.groupBuyProductImageEntity;
    private static final QGroupBuyOptionTypeEntity T = QGroupBuyOptionTypeEntity.groupBuyOptionTypeEntity;
    private static final QGroupBuyOptionAxisEntity A = QGroupBuyOptionAxisEntity.groupBuyOptionAxisEntity;
    private static final QGroupBuyOptionValueEntity V = QGroupBuyOptionValueEntity.groupBuyOptionValueEntity;
    private static final QGroupBuyProductSkuEntity PS = QGroupBuyProductSkuEntity.groupBuyProductSkuEntity;
    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuySkuEntity S = QGroupBuySkuEntity.groupBuySkuEntity;

    /** 카테고리 한 줄 */
    public record CategoryRow(long id, Long parentId, String name, int sortOrder, boolean hidden) {
    }

    /** 상품 한 줄 */
    public record ProductRow(long id, long categoryId, String name, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                             String normalPriceSource, String withdrawalRestriction, int version, Instant createdAt, Instant updatedAt) {
    }

    /** 상품 단품 — {@code valueIds} 축 값 조합(축 차례), {@code defaultSupplyPrice} null = 기본 공급가 없음 */
    public record ProductSkuRow(long id, long productId, List<Long> valueIds, String name, Long defaultSupplyPrice, boolean onSale, int sortOrder) {
    }

    /** 새 상품 칸 */
    public record NewProduct(long categoryId, String name, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                             String normalPriceSource, String withdrawalRestriction) {
    }

    private static final ConstructorExpression<CategoryRow> CATEGORY = Projections.constructor(CategoryRow.class, C.id, C.parentId, C.name, C.sortOrder, C.hidden);
    private static final ConstructorExpression<ProductRow> PRODUCT = Projections.constructor(ProductRow.class, P.id, P.categoryId, P.name, P.summary, P.spec, P.description,
            P.normalPrice, P.normalPriceBasis, P.normalPriceSource, P.withdrawalRestriction, P.version, P.createdAt, P.updatedAt);

    public GroupBuyProductStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    // ── 카테고리 ────────────────────────────────────────────────────────────

    public Optional<CategoryRow> category(long id) {
        return Optional.ofNullable(query.select(CATEGORY).from(C).where(C.id.eq(id)).fetchFirst());
    }

    /** 모든 카테고리(최상위 먼저, 차례대로) */
    public List<CategoryRow> categories() {
        return query.select(CATEGORY).from(C).orderBy(C.parentId.asc().nullsFirst(), C.sortOrder.asc(), C.id.asc()).fetch();
    }

    public void insertCategory(long id, Long parentId, String name, int sortOrder, Instant now) {
        insert(GroupBuyCategoryEntity.create(id, parentId, name, sortOrder, now));
    }

    public boolean updateCategory(long id, String name, int sortOrder, boolean hidden, Instant now) {
        return query.update(C).set(C.name, name).set(C.sortOrder, sortOrder).set(C.hidden, hidden).set(C.updatedAt, now).where(C.id.eq(id)).execute() == 1;
    }

    // ── 옵션 종류 ───────────────────────────────────────────────────────────

    /** 옵션 종류 한 줄 — 쓰는 상품 수·그 종류로 쓴 값(자동완성용, 가나다 차례) */
    public record OptionTypeRow(String code, String name, int sortOrder, boolean hidden, int productCount, List<String> usedValues) {
    }

    /** 모든 옵션 종류(차례대로) — 종류 한 번, 쓰는 상품 수 한 번, 쓴 값 한 번 */
    public List<OptionTypeRow> optionTypes() {
        List<Tuple> types = query.select(T.code, T.name, T.sortOrder, T.hidden).from(T).orderBy(T.sortOrder.asc(), T.code.asc()).fetch();
        Map<String, Long> productCounts = new HashMap<>();
        query.select(A.optionTypeCode, A.productId.countDistinct()).from(A).groupBy(A.optionTypeCode).fetch()
                .forEach(t -> productCounts.put(t.get(A.optionTypeCode), t.get(1, Long.class)));
        Map<String, List<String>> values = new HashMap<>();
        query.select(A.optionTypeCode, V.value).distinct().from(V).join(A).on(A.id.eq(V.axisId)).orderBy(V.value.asc()).fetch()
                .forEach(t -> values.computeIfAbsent(t.get(A.optionTypeCode), k -> new ArrayList<>()).add(t.get(V.value)));
        return types.stream().map(t -> new OptionTypeRow(t.get(T.code), t.get(T.name), n(t.get(T.sortOrder)), Boolean.TRUE.equals(t.get(T.hidden)),
                Math.toIntExact(productCounts.getOrDefault(t.get(T.code), 0L)), List.copyOf(values.getOrDefault(t.get(T.code), List.of())))).toList();
    }

    /** 옵션 종류 하나를 넣거나 이름·차례·숨김을 고친다(코드는 불변) — Native SQL 인 이유: 코드가 키인 {@code ON CONFLICT (code) DO UPDATE} upsert */
    public void upsertOptionType(String code, String name, int sortOrder, boolean hidden, Instant now) {
        jdbc.update("INSERT INTO group_buy_option_types (code, name, sort_order, hidden, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (code) DO UPDATE "
                + "SET name = excluded.name, sort_order = excluded.sort_order, hidden = excluded.hidden, updated_at = excluded.updated_at", code, name, sortOrder, hidden,
                Timestamp.from(now), Timestamp.from(now));
    }

    /** 이름 유일을 잠시 비켜 둔다(두 종류의 이름을 맞바꿔도 되게) */
    public void parkOptionTypeName(String code) {
        query.update(T).set(T.name, Expressions.asString("#").concat(T.code)).where(T.code.eq(code)).execute();
    }

    public void deleteOptionType(String code) {
        query.delete(T).where(T.code.eq(code)).execute();
    }

    // ── 상품 ────────────────────────────────────────────────────────────────

    public Optional<ProductRow> find(long id) {
        return Optional.ofNullable(query.select(PRODUCT).from(P).where(P.id.eq(id)).fetchFirst());
    }

    /**
     * 상품 행을 잠근다(구성 바꾸기 ∥ 그 상품을 쓰는 공구의 모집 시작 — 모집 시작도 이 행을 잠근다).
     *
     * <p>Native SQL({@code FOR UPDATE})인 이유: 공구 등록(DRAFT 넣기 — {@code GroupBuyAdminService#create}·다음 차수)은 상품 행을 잠그지 않고 그 상품을 FK 로 가리키는 공구 줄을
     * 넣는다. 구성 바꾸기는 이 잠금 뒤 그 상품의 DRAFT 공구를 골라 단품을 맞추고({@code draftGroupBuys}·{@code deleteDraftGroupSkus}) 진행 중 공구를 본다 —
     * {@code FOR UPDATE} 라야 넣는 중인 공구 줄의 FK 확인({@code FOR KEY SHARE})이 끝날(커밋될) 때까지 기다린 뒤 그 공구까지 맞춘다. JPA {@code PESSIMISTIC_WRITE}
     * ({@code FOR NO KEY UPDATE})면 기다리지 않아 막 넣은 DRAFT 공구가 새 구성을 놓친다.
     */
    public Optional<ProductRow> lock(long id) {
        boolean found = !entityManager.createNativeQuery("SELECT id FROM group_buy_products WHERE id = ?1 FOR UPDATE").setParameter(1, id).getResultList().isEmpty();
        return found ? find(id) : Optional.empty();
    }

    public void insert(long id, NewProduct p, Long operatorId, Instant now) {
        insert(GroupBuyProductEntity.create(id, p.categoryId(), p.name(), p.summary(), p.spec(), p.description(), p.normalPrice(), p.normalPriceBasis(),
                p.normalPriceSource(), p.withdrawalRestriction(), operatorId, now));
    }

    /** 모든 칸을 고친다(낙관 잠금) */
    public boolean update(long id, int version, NewProduct p, Instant now) {
        return query.update(P).set(P.categoryId, p.categoryId()).set(P.name, p.name()).set(P.summary, p.summary()).set(P.spec, p.spec()).set(P.description, p.description())
                .set(P.normalPrice, p.normalPrice()).set(P.normalPriceBasis, p.normalPriceBasis()).set(P.normalPriceSource, p.normalPriceSource())
                .set(P.withdrawalRestriction, p.withdrawalRestriction()).set(P.version, P.version.add(1)).set(P.updatedAt, now).where(P.id.eq(id), P.version.eq(version))
                .execute() == 1;
    }

    /** 이 상품(공구 상품 또는 추가 구성상품)을 쓰는 「모집 중」 공구가 있는가 */
    public boolean usedByActiveGroupBuy(long productId) {
        return !activeGroupBuys(productId).isEmpty();
    }

    /** 이 상품을 쓰는 「모집 중」 공구 — 공개된 예정·모집 중·집계 중(이 상태의 공구가 상품을 쓰면 이름·정상가·구성이 잠긴다) */
    public List<Long> activeGroupBuys(long productId) {
        return usingGroupBuys(productId, G.publishedAt.isNotNull().and(G.status.in(GroupBuyStatus.DRAFT, GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING)));
    }

    /** 이 상품을 쓰는, 모집을 시작했고 끝나지 않은 공구(상품 내용을 고치면 그 공구 수정 이력에 사본 — 결정 89) */
    public List<Long> runningGroupBuys(long productId) {
        return usingGroupBuys(productId, G.status.in(GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING, GroupBuyStatus.CLOSED_SUCCESS, GroupBuyStatus.ORDERED,
                GroupBuyStatus.SHIPPING));
    }

    /** 이 상품을 쓰는, 공개됐고 끝나지 않은 공구(고시를 비울 수 없다) */
    public List<Long> publishedGroupBuys(long productId) {
        return usingGroupBuys(productId, G.publishedAt.isNotNull().and(G.status.notIn(GroupBuyStatus.COMPLETED, GroupBuyStatus.CANCELED, GroupBuyStatus.CLOSED_FAILED)));
    }

    private List<Long> usingGroupBuys(long productId, BooleanExpression condition) {
        return query.select(G.id).from(G).where(condition, G.productId.eq(productId).or(JPAExpressions.selectOne().from(S).join(PS).on(PS.id.eq(S.productSkuId))
                .where(S.groupBuyId.eq(G.id), PS.productId.eq(productId)).exists())).orderBy(G.id.asc()).fetch();
    }

    // ── 이미지 ──────────────────────────────────────────────────────────────

    public List<ImageRow> images(long productId) {
        return query.select(Projections.constructor(ImageRow.class, IMG.imageKey, IMG.thumbKey, IMG.sortOrder)).from(IMG).where(IMG.productId.eq(productId))
                .orderBy(IMG.sortOrder.asc()).fetch();
    }

    public void replaceImages(long productId, List<ImageRow> images, List<Long> ids, Instant now) {
        query.delete(IMG).where(IMG.productId.eq(productId)).execute();
        List<GroupBuyProductImageEntity> rows = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            ImageRow image = images.get(i);
            rows.add(GroupBuyProductImageEntity.create(ids.get(i), productId, image.imageKey(), image.thumbKey(), i, now));
        }
        insertAll(rows);
    }

    // ── 옵션 축·값·상품 단품 ────────────────────────────────────────────────

    /** 옵션 축(옵션 종류 — 이름은 종류의 지금 이름)과 값(차례대로) — 축 한 번, 값 한 번 */
    public List<AxisRow> axes(long productId) {
        return GroupBuyStore.axesOfProduct(query, productId);
    }

    public void insertAxis(long id, long productId, int sortOrder, String optionTypeCode) {
        insert(GroupBuyOptionAxisEntity.create(id, productId, sortOrder, optionTypeCode));
    }

    public void deleteAxes(long productId) {
        query.delete(A).where(A.productId.eq(productId)).execute();
    }

    public void insertValue(long id, long axisId, long productId, int sortOrder, String value) {
        insert(GroupBuyOptionValueEntity.create(id, axisId, productId, sortOrder, value));
    }

    public void moveValue(long id, int sortOrder) {
        query.update(V).set(V.sortOrder, sortOrder).where(V.id.eq(id)).execute();
    }

    public void deleteValue(long id) {
        query.delete(V).where(V.id.eq(id)).execute();
    }

    public List<ProductSkuRow> skus(long productId) {
        return query.select(PS.id, PS.productId, PS.valueIds, PS.name, PS.defaultSupplyPrice, PS.onSale, PS.sortOrder).from(PS).where(PS.productId.eq(productId))
                .orderBy(PS.sortOrder.asc(), PS.id.asc()).fetch().stream().map(GroupBuyProductStore::sku).toList();
    }

    public Optional<ProductSkuRow> sku(long id) {
        return Optional.ofNullable(query.select(PS.id, PS.productId, PS.valueIds, PS.name, PS.defaultSupplyPrice, PS.onSale, PS.sortOrder).from(PS).where(PS.id.eq(id))
                .fetchFirst()).map(GroupBuyProductStore::sku);
    }

    private static ProductSkuRow sku(Tuple t) {
        return new ProductSkuRow(t.get(PS.id), t.get(PS.productId), list(t.get(PS.valueIds)), t.get(PS.name), t.get(PS.defaultSupplyPrice),
                Boolean.TRUE.equals(t.get(PS.onSale)), n(t.get(PS.sortOrder)));
    }

    public void insertSku(long id, long productId, List<Long> valueIds, String name, Long defaultSupplyPrice, boolean onSale, int sortOrder) {
        insert(GroupBuyProductSkuEntity.create(id, productId, array(valueIds), name, defaultSupplyPrice, onSale, sortOrder));
    }

    public void updateSku(long id, String name, List<Long> valueIds, Long defaultSupplyPrice, boolean onSale, int sortOrder) {
        query.update(PS).set(PS.name, name).set(PS.valueIds, array(valueIds)).set(PS.defaultSupplyPrice, defaultSupplyPrice).set(PS.onSale, onSale).set(PS.sortOrder, sortOrder)
                .where(PS.id.eq(id)).execute();
    }

    /** 상품 단품의 값·이름이 바뀌었다 — 공구 MAIN 단품의 조합 사본은 늘, 이름 사본은 공개 전 DRAFT 공구만 고친다(공개된 공구의 이름은 표시 기록) */
    public void updateGroupSkuCopies(long productSkuId, List<Long> valueIds, String name) {
        query.update(S).set(S.valueIds, array(valueIds)).where(S.productSkuId.eq(productSkuId), S.kind.eq(SkuKind.MAIN)).execute();
        query.update(S).set(S.name, name).where(S.productSkuId.eq(productSkuId), S.kind.eq(SkuKind.MAIN), S.groupBuyId.in(unpublishedDrafts())).execute();
    }

    /** 공개 전 DRAFT 공구(서브쿼리) */
    private static com.querydsl.jpa.JPQLQuery<Long> unpublishedDrafts() {
        return JPAExpressions.select(G.id).from(G).where(G.status.eq(GroupBuyStatus.DRAFT), G.publishedAt.isNull());
    }

    public void deleteSku(long id) {
        query.delete(PS).where(PS.id.eq(id)).execute();
    }

    /** 공개됐거나 모집을 시작한 공구의 단품이 가리키는 상품 단품인가(지울 수 없다 — 공개 전 DRAFT 공구는 상품 구성을 따라간다) */
    public boolean skuInUse(long id) {
        return query.selectOne().from(S).join(G).on(G.id.eq(S.groupBuyId)).where(S.productSkuId.eq(id), G.publishedAt.isNotNull().or(G.status.ne(GroupBuyStatus.DRAFT)))
                .fetchFirst() != null;
    }

    /** 공개 전 DRAFT 공구의 단품 중 이 상품 단품을 가리키는 것을 지운다(상품에서 없앤 단품) */
    public void deleteDraftGroupSkus(long productSkuId) {
        query.delete(S).where(S.productSkuId.eq(productSkuId), S.groupBuyId.in(unpublishedDrafts())).execute();
    }

    /** 이 상품을 파는 공개 전 DRAFT 공구(상품에 더한 단품을 MAIN 단품으로 더한다) */
    public List<Long> draftGroupBuys(long productId) {
        return query.select(G.id).from(G).where(G.productId.eq(productId), G.status.eq(GroupBuyStatus.DRAFT), G.publishedAt.isNull()).orderBy(G.id.asc()).fetch();
    }
}
