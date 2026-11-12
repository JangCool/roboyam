package com.community.pointshop.repository.master;

import com.community.operator.entity.QOperatorEntity;
import com.community.pointshop.entity.PointShopItemEntity;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopSettingEntity;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.entity.QPointShopItemEntity;
import com.community.pointshop.entity.QPointShopPurchaseEntity;
import com.community.pointshop.entity.QPointShopSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.community.zconfiguration.jpa.UniqueViolations;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 포인트 상점 품목·설정 읽기·쓰기 — {@code com.community.pointshop.service} 만 부른다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>QueryDSL 이 기본이다. 구매의 <b>재고 차감</b>은 조건부 UPDATE 한 문장({@code stock = stock - 1 WHERE id = ? AND stock > 0})이라 마지막 하나를 둘이 동시에 사도 하나만 이긴다
 * (commerce.md 7-2 — 먼저 고친 쪽의 행 잠금이 풀린 뒤 다른 쪽은 조건을 다시 봐 0 줄). 구매·환불은 포인트 사용·사용 취소({@code PointLedgerWriter}, JDBC)와 같은 트랜잭션·같은 연결이고
 * 잠금 차례(재고 → 잔액)는 부르는 서비스가 지킨다(JPA 트랜잭션 관리자가 JDBC 에도 같은 연결을 준다). 읽기는 한 줄 모양(프로젝션)이라 영속성 문맥의 낡은 엔티티를 보지 않는다.
 * 넣기는 넣고 바로 내보낸다(유일 위반은 그 자리에서 {@code DuplicateKeyException} — JDBC 때와 같다, {@link UniqueViolations}).
 * Native SQL 로 남긴 것과 이유: 설정 행 「없으면 만든다」({@code INSERT … ON CONFLICT (id) DO NOTHING} — upsert).
 */
@Repository
public class PointShopItemStore extends IrobitRepositoryQDSL {

    /** 품목 한 줄(만든·고친 운영자 이름 포함 — 시스템(마이그레이션)이거나 지워진 운영자면 null) */
    public record ItemRow(long id, String code, PointShopItemKind kind, PointShopUsageType usageType, String name, String nameEn, String description,
                          String descriptionEn, String color, String borderStyle, String avatarId, int price, Integer durationDays, Integer stock, Instant saleStartAt,
                          Instant saleEndAt, boolean active, int sortOrder, Long createdBy, String createdByName, Instant createdAt, Long updatedBy,
                          String updatedByName, Instant updatedAt) {
    }

    /** 새로 넣거나 고칠 값(정체 칸 code·kind·avatar_id 는 넣을 때만 쓴다) */
    public record ItemValues(String code, PointShopItemKind kind, PointShopUsageType usageType, String name, String nameEn, String description, String descriptionEn,
                             String color, String borderStyle, String avatarId, int price, Integer durationDays, Integer stock, Instant saleStartAt, Instant saleEndAt,
                             boolean active, int sortOrder) {
    }

    /** 설정 행(마지막으로 저장한 운영자 이름 포함) */
    public record SettingsRow(int refundDays, Instant updatedAt, Long updatedBy, String updatedByName) {
    }

    private static final QPointShopItemEntity I = QPointShopItemEntity.pointShopItemEntity;
    private static final QOperatorEntity CREATED = new QOperatorEntity("itemCreatedBy");
    private static final QOperatorEntity UPDATED = new QOperatorEntity("itemUpdatedBy");
    private static final QPointShopPurchaseEntity P = QPointShopPurchaseEntity.pointShopPurchaseEntity;
    private static final QPointShopSettingEntity S = QPointShopSettingEntity.pointShopSettingEntity;
    private static final QOperatorEntity SAVED = new QOperatorEntity("settingUpdatedBy");

    private final PointShopItemRepository items;
    private final EntityManager entityManager;

    public PointShopItemStore(JPAQueryFactory query, PointShopItemRepository items, EntityManager entityManager) {
        super(query);
        this.items = items;
        this.entityManager = entityManager;
    }

    // ── 품목 읽기 ───────────────────────────────────────────────────────────

    public Optional<ItemRow> find(long id) {
        return Optional.ofNullable(itemRows().where(I.id.eq(id)).fetchOne()).map(PointShopItemStore::itemOf);
    }

    /**
     * 고치기 전에 — 행을 잠근다(같은 품목의 동시 저장이 전→후 감사를 어긋나게 하지 않게). 잠금은 품목 표만 고르는 한 문장({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE}
     * — 품목을 FK 로 가리키는 구매·보유·장착 줄이 넣어져도 고치기의 판정은 그 줄을 보지 않는다), 칸은 그 뒤 운영자 이름을 잇는 한 줄 모양으로 읽는다(조인한 프로젝션에 잠금을 걸면
     * 잠기지 않는다).
     */
    public Optional<ItemRow> findForUpdate(long id) {
        return query.select(I.id).from(I).where(I.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne() != null ? find(id) : Optional.empty();
    }

    public Optional<ItemRow> findByCode(String code) {
        return Optional.ofNullable(itemRows().where(I.code.eq(code)).fetchOne()).map(PointShopItemStore::itemOf);
    }

    /**
     * 목록 — 순서 → ID 차례.
     *
     * @param kind        이 종류만(null = 전부)
     * @param excludeKind 이 종류 빼고(null = 빼지 않음)
     */
    public List<ItemRow> list(PointShopItemKind kind, PointShopItemKind excludeKind) {
        BooleanBuilder where = new BooleanBuilder();
        if (kind != null) {
            where.and(I.kind.eq(kind));
        }
        if (excludeKind != null) {
            where.and(I.kind.ne(excludeKind));
        }
        return itemRows().where(where).orderBy(I.sortOrder.asc(), I.id.asc()).fetch().stream().map(PointShopItemStore::itemOf).toList();
    }

    public List<ItemRow> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return itemRows().where(I.id.in(ids)).orderBy(I.sortOrder.asc(), I.id.asc()).fetch().stream().map(PointShopItemStore::itemOf).toList();
    }

    /** 아바타 ID → 아바타 품목 */
    public Map<String, ItemRow> avatarItems() {
        Map<String, ItemRow> out = new HashMap<>();
        list(PointShopItemKind.PROFILE_AVATAR, null).forEach(row -> out.put(row.avatarId(), row));
        return out;
    }

    public boolean codeExists(String code) {
        return query.selectOne().from(I).where(I.code.eq(code)).fetchFirst() != null;
    }

    /** 품목마다 판 수(환불한 구매는 빼고) */
    public Map<Long, Integer> soldCounts(Collection<Long> itemIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (itemIds.isEmpty()) {
            return out;
        }
        query.select(P.itemId, P.count()).from(P).where(P.status.eq(PointShopPurchaseStatus.COMPLETED), P.itemId.in(itemIds)).groupBy(P.itemId).fetch()
                .forEach(t -> out.put(t.get(P.itemId), t.get(P.count()).intValue()));
        return out;
    }

    // ── 품목 쓰기 ───────────────────────────────────────────────────────────

    public void insert(long id, ItemValues v, long operatorId, Instant now) {
        UniqueViolations.inserting(() -> {
            items.persist(PointShopItemEntity.create(id, v.code(), v.kind(), v.usageType(), v.name(), v.nameEn(), v.description(), v.descriptionEn(), v.color(),
                    v.borderStyle(), v.avatarId(), v.price(), v.durationDays(), v.stock(), v.saleStartAt(), v.saleEndAt(), v.active(), v.sortOrder(), operatorId, now));
            items.flush();
        });
    }

    /** 바꿀 수 있는 칸을 모두 바꾼다(정체 칸 code·kind·avatar_id 는 그대로 — 트리거도 막는다) */
    public void update(long id, ItemValues v, long operatorId, Instant now) {
        query.update(I).set(I.usageType, v.usageType()).set(I.name, v.name()).set(I.nameEn, v.nameEn()).set(I.description, v.description())
                .set(I.descriptionEn, v.descriptionEn()).set(I.color, v.color()).set(I.borderStyle, v.borderStyle()).set(I.pricePoint, v.price())
                .set(I.durationDays, v.durationDays()).set(I.stock, v.stock()).set(I.saleStartAt, v.saleStartAt()).set(I.saleEndAt, v.saleEndAt())
                .set(I.active, v.active()).set(I.sortOrder, v.sortOrder()).set(I.updatedBy, operatorId).set(I.updatedAt, now)
                .where(I.id.eq(id)).execute();
    }

    /**
     * 재고를 하나 뺀다(한정 재고만) — 남은 재고가 있을 때만 한 문장으로. 동시에 마지막 하나를 사면 먼저 잠근 쪽만 이기고 나머지는 행 잠금이 풀린 뒤 조건을 다시 봐 0 줄이다.
     *
     * @return 뺐으면 true(무제한 품목은 늘 true — 바꾸지 않는다)
     */
    public boolean takeStock(long id) {
        if (query.update(I).set(I.stock, I.stock.subtract(1)).where(I.id.eq(id), I.stock.isNotNull(), I.stock.gt(0)).execute() == 1) {
            return true;
        }
        return query.selectOne().from(I).where(I.id.eq(id), I.stock.isNull()).fetchFirst() != null;
    }

    /** 환불한 구매의 재고를 되돌린다(지금 한정 재고인 품목만 — 무제한으로 바뀌었으면 그대로) */
    public void restoreStock(long id) {
        query.update(I).set(I.stock, I.stock.add(1)).where(I.id.eq(id), I.stock.isNotNull()).execute();
    }

    /** 아바타 품목 묶음 가격 — 범위 안에서 값이 다른 줄만 바꾼다. 바꾼 줄 수 */
    public int setAvatarPrice(Collection<Long> ids, int price, long operatorId, Instant now) {
        if (ids.isEmpty()) {
            return 0;
        }
        return (int) query.update(I).set(I.pricePoint, price).set(I.updatedBy, operatorId).set(I.updatedAt, now)
                .where(I.kind.eq(PointShopItemKind.PROFILE_AVATAR), I.pricePoint.ne(price), I.id.in(ids)).execute();
    }

    /** 아바타 품목 묶음 판매 켜기·끄기 — 값이 다른 줄만. 바꾼 줄 수 */
    public int setAvatarActive(Collection<Long> ids, boolean active, long operatorId, Instant now) {
        if (ids.isEmpty()) {
            return 0;
        }
        return (int) query.update(I).set(I.active, active).set(I.updatedBy, operatorId).set(I.updatedAt, now)
                .where(I.kind.eq(PointShopItemKind.PROFILE_AVATAR), I.active.ne(active), I.id.in(ids)).execute();
    }

    // ── 설정 ────────────────────────────────────────────────────────────────

    /** 셀프 환불 기한 일수(행이 없으면 기본값) */
    public int refundDays() {
        Integer days = query.select(S.refundDays).from(S).where(S.id.eq(PointShopSettingEntity.ROW_ID)).fetchOne();
        return days == null ? PointShopSettingEntity.DEFAULT_REFUND_DAYS : days;
    }

    public SettingsRow settings() {
        Tuple t = query.select(S.refundDays, S.updatedAt, S.updatedBy, SAVED.operatorName).from(S).leftJoin(SAVED).on(SAVED.id.eq(S.updatedBy))
                .where(S.id.eq(PointShopSettingEntity.ROW_ID)).fetchOne();
        return t == null ? new SettingsRow(PointShopSettingEntity.DEFAULT_REFUND_DAYS, null, null, null)
                : new SettingsRow(t.get(S.refundDays), t.get(S.updatedAt), t.get(S.updatedBy), t.get(SAVED.operatorName));
    }

    /**
     * 설정 행을 잠근 뒤 지금 값(같은 화면의 동시 저장이 전→후 감사를 어긋나게 하지 않게).
     *
     * <p>행 만들기가 Native SQL 인 이유: {@code INSERT … ON CONFLICT (id) DO NOTHING} — 「없으면 만든다」를 동시 저장이 겹쳐도 한 문장으로(upsert 는 JPQL·QueryDSL 에 없다).
     * 잠금은 설정 표만 고르는 한 문장({@code PESSIMISTIC_WRITE} = {@code FOR NO KEY UPDATE} — 이 표를 FK 로 가리키는 표가 없다).
     */
    public int lockRefundDays() {
        entityManager.createNativeQuery("INSERT INTO point_shop_settings (id) VALUES (?1) ON CONFLICT (id) DO NOTHING").setParameter(1, PointShopSettingEntity.ROW_ID)
                .executeUpdate();
        return query.select(S.refundDays).from(S).where(S.id.eq(PointShopSettingEntity.ROW_ID)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne();
    }

    public void updateRefundDays(int days, long operatorId, Instant now) {
        query.update(S).set(S.refundDays, days).set(S.updatedBy, operatorId).set(S.updatedAt, now).where(S.id.eq(PointShopSettingEntity.ROW_ID)).execute();
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private JPAQuery<Tuple> itemRows() {
        return query.select(I.id, I.code, I.kind, I.usageType, I.name, I.nameEn, I.description, I.descriptionEn, I.color, I.borderStyle, I.avatarId, I.pricePoint,
                        I.durationDays, I.stock, I.saleStartAt, I.saleEndAt, I.active, I.sortOrder, I.createdBy, CREATED.operatorName, I.createdAt, I.updatedBy,
                        UPDATED.operatorName, I.updatedAt)
                .from(I).leftJoin(CREATED).on(CREATED.id.eq(I.createdBy)).leftJoin(UPDATED).on(UPDATED.id.eq(I.updatedBy));
    }

    private static ItemRow itemOf(Tuple t) {
        return new ItemRow(t.get(I.id), t.get(I.code), t.get(I.kind), t.get(I.usageType), t.get(I.name), t.get(I.nameEn), t.get(I.description), t.get(I.descriptionEn),
                t.get(I.color), t.get(I.borderStyle), t.get(I.avatarId), t.get(I.pricePoint), t.get(I.durationDays), t.get(I.stock), t.get(I.saleStartAt),
                t.get(I.saleEndAt), t.get(I.active), t.get(I.sortOrder), t.get(I.createdBy), t.get(CREATED.operatorName), t.get(I.createdAt), t.get(I.updatedBy),
                t.get(UPDATED.operatorName), t.get(I.updatedAt));
    }

    /** native 인자 — {@code timestamptz} 로 보낸다 */
    static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
