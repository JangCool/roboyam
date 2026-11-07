package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyDelayNoticeEntity;
import com.community.groupbuy.entity.GroupBuyEntity;
import com.community.groupbuy.entity.GroupBuySettingsEntity;
import com.community.groupbuy.entity.GroupBuySkuEntity;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.QGroupBuyCategoryEntity;
import com.community.groupbuy.entity.QGroupBuyDelayNoticeEntity;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyOptionAxisEntity;
import com.community.groupbuy.entity.QGroupBuyOptionTypeEntity;
import com.community.groupbuy.entity.QGroupBuyOptionValueEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuySettingsEntity;
import com.community.groupbuy.entity.QGroupBuySkuEntity;
import com.community.groupbuy.entity.QGroupBuySupplyFailureEntity;
import com.community.groupbuy.entity.QGroupBuyWatchEntity;
import com.community.groupbuy.entity.SkuKind;
import com.community.groupbuy.entity.WatchKind;
import com.community.holiday.entity.QSiteHolidayEntity;
import com.community.holiday.service.BusinessDayCalendar;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * 공구({@code group_buys} — 상품 칸은 {@code group_buy_products} 를 이어 읽는다, 결정 89)·공구 단품·수정 이력·상태 이력·지연 공지·공구 설정 읽기·쓰기. 수량 열은 여기서 바꾸지 않는다({@link GroupBuyQtyStore} — 수량을 바꾸는 유일한 길).
 * 상태 전이는 한 문장 조건부 UPDATE(이긴 쪽만, commerce.md 3절 — QueryDSL {@code update().where(id, status).execute() == 1}).
 *
 * <p>QueryDSL 이 기본이다(읽기는 한 줄 모양, 쓰기는 즉시 실행 — {@link GroupBuyStoreSupport}). 판정 ①의 공구 행 잠금({@link #lock})은 공구 행만 고르는 잠금 문장 뒤에 읽는다.
 * Native SQL 로 남긴 것과 이유: 공구 상태 이력 넣기({@link #insertStatusHistory} — 결제 확정 세이브포인트 안에서도 넣는다), 수정 이력 사본({@link #snapshotRevision} — jsonb_agg 로 상품·옵션·단품·고시를 한 문장 INSERT … SELECT), 진행 중 주문이 없는 공구의 완료 sweep
 * ({@link #completeFinished} — 일괄 {@code UPDATE … RETURNING}, 바꾼 공구를 같은 문장에서 받는다).
 */
@Repository
public class GroupBuyStore extends GroupBuyStoreSupport {

    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QGroupBuySkuEntity S = QGroupBuySkuEntity.groupBuySkuEntity;
    private static final QGroupBuyCategoryEntity C = QGroupBuyCategoryEntity.groupBuyCategoryEntity;
    private static final QGroupBuyCategoryEntity PARENT = new QGroupBuyCategoryEntity("parentCategory");
    private static final QGroupBuyOptionAxisEntity A = QGroupBuyOptionAxisEntity.groupBuyOptionAxisEntity;
    private static final QGroupBuyOptionTypeEntity T = QGroupBuyOptionTypeEntity.groupBuyOptionTypeEntity;
    private static final QGroupBuyOptionValueEntity V = QGroupBuyOptionValueEntity.groupBuyOptionValueEntity;
    private static final QGroupBuySettingsEntity SET = QGroupBuySettingsEntity.groupBuySettingsEntity;
    private static final QGroupBuySupplyFailureEntity SF = QGroupBuySupplyFailureEntity.groupBuySupplyFailureEntity;
    private static final QGroupBuyWatchEntity W = QGroupBuyWatchEntity.groupBuyWatchEntity;
    private static final QGroupBuyDelayNoticeEntity D = QGroupBuyDelayNoticeEntity.groupBuyDelayNoticeEntity;
    private static final QSiteHolidayEntity H = QSiteHolidayEntity.siteHolidayEntity;

    /** 공구 한 줄(상품 칸 포함) */
    public record GroupBuyRow(long id, long productId, long categoryId, String title, String productName, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                              String normalPriceSource, long groupPrice, String shippingPolicyCode, Long shippingFee, Long freeShippingOver, Long returnShippingFee,
                              String returnFeeMode, int minQty, int maxQty, Integer initialMaxQty, int addedQty, Integer maxQtyPerUser, int currentQty, int reservedQty,
                              Instant startAt, Instant endAt, String extensionMode, Instant extendedUntil, Instant closingAt, String closeReason, String fulfillment,
                              LocalDate shipPlannedAt, Integer shipWithinBusinessDays, String withdrawalRestriction, String returnPolicy, GroupBuyStatus status,
                              Instant publishedAt, long roundRootId, int roundNo, boolean roundLimitShared, int revisionNo, int version, Long createdBy, Instant createdAt,
                              Instant updatedAt) {

        /** 모집 마감 시각(연장됐으면 연장 끝) */
        public Instant deadline() {
            return extendedUntil != null ? extendedUntil : endAt;
        }

        public boolean immediate() {
            return "IMMEDIATE".equals(fulfillment);
        }
    }

    /** 공구 + 상품 칸(이름·설명·정상가·청약철회 제한은 상품 것 — 결정 89) */
    private static final ConstructorExpression<GroupBuyRow> ROW = Projections.constructor(GroupBuyRow.class, G.id, G.productId, P.categoryId, G.title, P.name, P.summary,
            P.spec, P.description, P.normalPrice, P.normalPriceBasis, P.normalPriceSource, G.groupPrice, G.shippingPolicyCode, G.shippingFee, G.freeShippingOver,
            G.returnShippingFee, G.returnFeeMode, G.minQty, G.maxQty, G.initialMaxQty, G.addedQty, G.maxQtyPerUser, G.currentQty, G.reservedQty, G.startAt, G.endAt,
            G.extensionMode, G.extendedUntil, G.closingAt, G.closeReason, G.fulfillment, G.shipPlannedAt, G.shipWithinBusinessDays, P.withdrawalRestriction, G.returnPolicy,
            G.status, G.publishedAt, G.roundRootId, G.roundNo, G.roundLimitShared, G.revisionNo, G.version, G.createdBy, G.createdAt, G.updatedAt);

    /**
     * 공구 단품 한 줄(결정 88·89) — {@code kind} MAIN 본 상품 / ADDON 추가 구성상품, {@code productSkuId} 상품 단품, {@code valueIds} 축 값 조합(MAIN, 축 차례),
     * {@code maxQty} null = 단품 한도 없음, {@code supplyPrice} 공급가(null = 아직 없음 — 모집 시작 때 필수, 회원 응답에 싣지 않는다).
     */
    public record SkuRow(long id, long groupBuyId, SkuKind kind, long productSkuId, List<Long> valueIds, String name, long extraPrice, Long addonPrice, Integer maxQty,
                         Integer initialMaxQty, int currentQty, int reservedQty, boolean onSale, Long supplyPrice, int sortOrder) {

        public boolean main() {
            return kind == SkuKind.MAIN;
        }

        /** 단가 — MAIN = 공구가 + 추가금, ADDON = 자기 가격 */
        public long unitPrice(long groupPrice) {
            return main() ? Math.addExact(groupPrice, extraPrice) : addonPrice;
        }
    }

    /** 옵션 축(차례대로)과 값 — {@code code} 옵션 종류, {@code name} 그 종류의 지금 이름 */
    public record AxisRow(long id, String code, String name, int sortOrder, List<ValueRow> values) {
    }

    /** 축 값 */
    public record ValueRow(long id, long axisId, String value, int sortOrder) {
    }

    /** 대표 이미지 한 줄 */
    public record ImageRow(String imageKey, String thumbKey, int sortOrder) {
    }

    /**
     * 공구 설정(1행) — 돈·시각에 닿는 값은 그 트랜잭션 안에서 읽는다(결정 19).
     *
     * @param calendar 영업일 판정 — 사이트 쉬는 날 표({@code site_holidays}) 전부를 같은 트랜잭션에서 읽는다(공구 설정에 따로 휴일 칸이 없다 — 사용자 지시 2026-11-05)
     */
    public record Settings(int holdMinutes, int demandMaxDays, int firstComeMaxDays, int extensionMinutes, int extensionWindowMinutes, int judgeGraceMinutes,
                           int shipWithinBusinessDays, BusinessDayCalendar calendar, long compensationCap, long largeRefundThreshold, int addressRevealMinutes,
                           int supplyChoiceBusinessDays) {
    }

    public GroupBuyStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    // ── 설정 ────────────────────────────────────────────────────────────────

    public Settings settings() {
        Tuple s = query.select(SET.holdMinutes, SET.demandMaxDays, SET.firstComeMaxDays, SET.extensionMinutes, SET.extensionWindowMinutes, SET.judgeGraceMinutes,
                SET.shipWithinBusinessDays, SET.compensationCap, SET.largeRefundThreshold, SET.addressRevealMinutes, SET.supplyChoiceBusinessDays).from(SET)
                .where(SET.id.eq(GroupBuySettingsEntity.ROW_ID)).fetchFirst();
        if (s == null) {
            throw new EmptyResultDataAccessException(1); // 설정 행은 마이그레이션이 넣는다
        }
        // 쉬는 날은 따로 읽는다(예전 한 문장의 하위 질의) — 설정 행과 쉬는 날 표는 서로 다른 화면이 따로 저장하는 값이라 둘 사이에 지켜야 할 짝이 없다(나눠 읽어도 섞인 상태가 생기지 않는다)
        List<LocalDate> holidays = query.select(H.holidayDate).distinct().from(H).orderBy(H.holidayDate.asc()).fetch();
        return new Settings(n(s.get(SET.holdMinutes)), n(s.get(SET.demandMaxDays)), n(s.get(SET.firstComeMaxDays)), n(s.get(SET.extensionMinutes)),
                n(s.get(SET.extensionWindowMinutes)), n(s.get(SET.judgeGraceMinutes)), n(s.get(SET.shipWithinBusinessDays)), BusinessDayCalendar.of(holidays),
                n(s.get(SET.compensationCap)), n(s.get(SET.largeRefundThreshold)), n(s.get(SET.addressRevealMinutes)), n(s.get(SET.supplyChoiceBusinessDays)));
    }

    // ── 공구 ────────────────────────────────────────────────────────────────

    private JPAQuery<GroupBuyRow> rows() {
        return query.select(ROW).from(G).join(P).on(P.id.eq(G.productId));
    }

    public Optional<GroupBuyRow> find(long id) {
        return Optional.ofNullable(rows().where(G.id.eq(id)).fetchFirst());
    }

    /** 행을 잠그고 읽는다 — 공구 행을 잠그는 일(판정·중단)은 결제 행을 잠그는 일과 같은 트랜잭션에 넣지 않는다(결정 37). 공구 행만 잠그고(상품 행은 잠그지 않는다) 그다음 읽는다 */
    public Optional<GroupBuyRow> lock(long id) {
        return lockRow(G, G.id, id) ? find(id) : Optional.empty();
    }

    /** 새 공구(DRAFT) — 1차면 {@code roundRootId = id} */
    public void insert(long id, NewGroupBuy g, long roundRootId, int roundNo, Long operatorId, Instant now) {
        insert(GroupBuyEntity.draft(id, g.productId(), g.title(), g.groupPrice(), g.shippingPolicyCode(), g.minQty(), g.maxQty(), g.maxQtyPerUser(), g.startAt(), g.endAt(),
                g.extensionMode(), g.fulfillment(), g.shipPlannedAt(), g.shipWithinBusinessDays(), g.returnPolicy(), roundRootId, roundNo, g.roundLimitShared(), operatorId,
                now));
    }

    /** 공구 칸(DRAFT 의 모든 칸 — 상품 하나 + 판매 조건) */
    public record NewGroupBuy(long productId, String title, long groupPrice, String shippingPolicyCode, int minQty, int maxQty, Integer maxQtyPerUser, Instant startAt,
                              Instant endAt, String extensionMode, String fulfillment, LocalDate shipPlannedAt, Integer shipWithinBusinessDays, String returnPolicy,
                              boolean roundLimitShared) {
    }

    /** DRAFT 의 모든 칸을 고친다(낙관 잠금 — 버전이 같고 아직 DRAFT 일 때만) */
    public boolean updateDraft(long id, int version, NewGroupBuy g, Instant now) {
        return query.update(G).set(G.productId, g.productId()).set(G.title, g.title()).set(G.groupPrice, g.groupPrice()).set(G.shippingPolicyCode, g.shippingPolicyCode())
                .set(G.minQty, g.minQty()).set(G.maxQty, g.maxQty()).set(G.maxQtyPerUser, g.maxQtyPerUser()).set(G.startAt, g.startAt()).set(G.endAt, g.endAt())
                .set(G.extensionMode, g.extensionMode()).set(G.fulfillment, g.fulfillment()).set(G.shipPlannedAt, g.shipPlannedAt())
                .set(G.shipWithinBusinessDays, g.shipWithinBusinessDays()).set(G.returnPolicy, g.returnPolicy()).set(G.roundLimitShared, g.roundLimitShared())
                .set(G.version, G.version.add(1)).set(G.updatedAt, now)
                .where(G.id.eq(id), G.version.eq(version), G.status.eq(GroupBuyStatus.DRAFT), G.publishedAt.isNull()).execute() == 1;
    }

    /** 공개 뒤 공급가만 고쳤다 — 버전을 올린다(낙관 잠금) */
    public boolean touch(long id, int version, Instant now) {
        return query.update(G).set(G.version, G.version.add(1)).set(G.updatedAt, now).where(G.id.eq(id), G.version.eq(version)).execute() == 1;
    }

    /** 회원 목록(결정 89 카테고리 필터) — 공개된 공구(중단 제외), 카테고리를 주면 그 카테고리와 그 아래 단, 숨긴 카테고리는 빼고. 새 공구 먼저 */
    public List<Long> publicList(Long categoryId, int limit) {
        JPAQuery<Long> q = query.select(G.id).from(G).join(P).on(P.id.eq(G.productId)).join(C).on(C.id.eq(P.categoryId)).leftJoin(PARENT).on(PARENT.id.eq(C.parentId))
                .where(G.publishedAt.isNotNull(), G.status.ne(GroupBuyStatus.CANCELED), C.hidden.isFalse(), PARENT.hidden.isNull().or(PARENT.hidden.isFalse()));
        if (categoryId != null) {
            q.where(C.id.eq(categoryId).or(C.parentId.eq(categoryId)));
        }
        return q.orderBy(G.startAt.desc(), G.id.desc()).limit(limit).fetch();
    }

    /** 수정 이력 번호를 하나 올린다(수량 변경·지연 공지 — 표시 기록). 새 번호(올린 행 잠금을 쥔 채 같은 트랜잭션에서 읽는다) */
    public int nextRevision(long id, Instant now) {
        long updated = query.update(G).set(G.revisionNo, G.revisionNo.add(1)).set(G.updatedAt, now).where(G.id.eq(id)).execute();
        if (updated != 1) {
            throw new EmptyResultDataAccessException(1);
        }
        return query.select(G.revisionNo).from(G).where(G.id.eq(id)).fetchOne();
    }

    /** 운영자가 「모집 시작」을 눌렀다(공개 — 시작 시각 전이면 예정 공구) */
    public boolean publish(long id, int version, Instant now) {
        return query.update(G).set(G.publishedAt, now).set(G.version, G.version.add(1)).set(G.updatedAt, now)
                .where(G.id.eq(id), G.version.eq(version), G.status.eq(GroupBuyStatus.DRAFT), G.publishedAt.isNull()).execute() == 1;
    }

    /** 공개됐고 시작 시각이 된 DRAFT(상태 워커가 모집을 시작한다) */
    public List<Long> dueToStart(Instant now, int limit) {
        return query.select(G.id).from(G).where(G.status.eq(GroupBuyStatus.DRAFT), G.publishedAt.isNotNull(), G.startAt.loe(now)).orderBy(G.startAt.asc(), G.id.asc())
                .limit(limit).fetch();
    }

    /**
     * DRAFT → RECRUITING — 그 시각에 유효한 배송비 정책 줄의 값을 복사하고 처음 수량을 고정한다(10-2). 공개됐고 시작 시각이 된 DRAFT 만(이긴 쪽만 true).
     */
    public boolean start(long id, ShippingPolicyStore.PolicyRow policy, Instant now) {
        return query.update(G).set(G.status, GroupBuyStatus.RECRUITING).set(G.shippingFee, policy.baseFee()).set(G.freeShippingOver, policy.freeShippingOver())
                .set(G.returnShippingFee, policy.returnFee()).set(G.returnFeeMode, policy.returnFeeMode()).set(G.initialMaxQty, G.maxQty).set(G.revisionNo, 1)
                .set(G.updatedAt, now).where(G.id.eq(id), G.status.eq(GroupBuyStatus.DRAFT), G.publishedAt.isNotNull(), G.startAt.loe(now)).execute() == 1;
    }

    /** 모집 시작 때 단품의 처음 수량을 고정한다 */
    public void fixSkuInitial(long groupBuyId) {
        query.update(S).set(S.initialMaxQty, S.maxQty).where(S.groupBuyId.eq(groupBuyId)).execute();
    }

    /** 마감 시각이 지난 모집 중 공구 */
    public List<Long> recruitingPastDeadline(Instant now, int limit) {
        return query.select(G.id).from(G).where(G.status.eq(GroupBuyStatus.RECRUITING), G.extendedUntil.coalesce(G.endAt).loe(now)).orderBy(G.endAt.asc(), G.id.asc())
                .limit(limit).fetch();
    }

    /** 마감 시각에 연장한다(원래 마감에서 한 번 — 결정 50: 확정과 둘 다 RECRUITING 조건부라 하나만 이긴다) */
    public boolean extend(long id, Instant until, Instant now) {
        return query.update(G).set(G.extendedUntil, until).set(G.updatedAt, now)
                .where(G.id.eq(id), G.status.eq(GroupBuyStatus.RECRUITING), G.extendedUntil.isNull(), G.endAt.lt(until)).execute() == 1;
    }

    /** 기간 끝 → CLOSING(closing_at = 마감 시각 또는 연장 끝) */
    public boolean closeTimeUp(long id, Instant now) {
        return query.update(G).set(G.status, GroupBuyStatus.CLOSING).set(G.closeReason, "TIME_UP").set(G.closingAt, G.extendedUntil.coalesce(G.endAt))
                .set(G.updatedAt, now).where(G.id.eq(id), G.status.eq(GroupBuyStatus.RECRUITING), G.extendedUntil.coalesce(G.endAt).loe(now)).execute() == 1;
    }

    /** 조기 마감(운영자) → CLOSING(closing_at = 지금) */
    public boolean closeEarly(long id, Instant now) {
        return query.update(G).set(G.status, GroupBuyStatus.CLOSING).set(G.closeReason, "EARLY_CLOSED").set(G.closingAt, now).set(G.updatedAt, now)
                .where(G.id.eq(id), G.status.eq(GroupBuyStatus.RECRUITING)).execute() == 1;
    }

    /**
     * 다 팔림 조건 — 공구 최대(MAIN 수량)에 닿았거나, 판매 중인 MAIN 단품이 모두 한도가 있고 모두 확정 = 한도(결정 27·48·78·88).
     * 추가 구성상품은 공구 마감을 정하지 않는다.
     */
    /** {@link #soldOut} 과 같은 조건의 SQL(공구 별칭 {@code g}) — 세이브포인트 안의 다 팔림 마감({@code GroupBuyQtyStore#closeIfSoldOut}, JDBC)이 쓴다 */
    static final String SOLD_OUT_CONDITION = "(g.current_qty = g.max_qty OR NOT EXISTS (SELECT 1 FROM group_buy_skus s WHERE s.group_buy_id = g.id AND s.kind = 'MAIN' "
            + "AND s.on_sale AND (s.max_qty IS NULL OR s.current_qty < s.max_qty)))";

    static BooleanExpression soldOut(QGroupBuyEntity g) {
        QGroupBuySkuEntity s = new QGroupBuySkuEntity("soldOutSku");
        return g.currentQty.eq(g.maxQty).or(JPAExpressions.selectOne().from(s)
                .where(s.groupBuyId.eq(g.id), s.kind.eq(SkuKind.MAIN), s.onSale.isTrue(), s.maxQty.isNull().or(s.currentQty.lt(s.maxQty))).notExists());
    }

    /** 다 팔림 안전망 대상(10-7 ③ · 결정 78) */
    public List<Long> recruitingSoldOut(int limit) {
        return query.select(G.id).from(G).where(G.status.eq(GroupBuyStatus.RECRUITING), soldOut(G)).orderBy(G.id.asc()).limit(limit).fetch();
    }

    /** 집계 중이고 판정할 때가 된 공구(남은 선점 0 이거나 closing_at + 선점 시간 + 여유가 지남) */
    public List<Long> closingDue(Instant now, int holdMinutes, int graceMinutes, int limit) {
        Instant closedBefore = now.minus(Duration.ofMinutes(holdMinutes + graceMinutes)); // closing_at + (선점 + 여유) <= now
        return query.select(G.id).from(G).where(G.status.eq(GroupBuyStatus.CLOSING), G.reservedQty.eq(0).or(G.closingAt.loe(closedBefore)))
                .orderBy(G.closingAt.asc(), G.id.asc()).limit(limit).fetch();
    }

    /** 판정 결과로 전이(잠근 행을 판단한 뒤 — 결정 37 ①) */
    public boolean judge(long id, boolean success, Instant now) {
        return query.update(G).set(G.status, success ? GroupBuyStatus.CLOSED_SUCCESS : GroupBuyStatus.CLOSED_FAILED).set(G.updatedAt, now)
                .where(G.id.eq(id), G.status.eq(GroupBuyStatus.CLOSING)).execute() == 1;
    }

    /** 중단 — 종료 사유 CANCELED(결정 29 ①) */
    public boolean cancel(long id, Instant now) {
        return query.update(G).set(G.status, GroupBuyStatus.CANCELED).set(G.closeReason, "CANCELED").set(G.updatedAt, now)
                .where(G.id.eq(id), G.status.in(GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING, GroupBuyStatus.CLOSED_SUCCESS, GroupBuyStatus.ORDERED, GroupBuyStatus.SHIPPING))
                .execute() == 1;
    }

    /**
     * 진행 중 주문이 없는 공구를 COMPLETED 로 — 즉시 발송은 CLOSED_SUCCESS, 마감 후 발주는 SHIPPING 에서(결정 9 — 1분 워커의 조건부 sweep). 바꾼 공구.
     * Native SQL 인 이유: 일괄 {@code UPDATE … RETURNING} — 바꾼 공구(이긴 쪽)를 같은 문장에서 받는다(따로 고르면 그 사이 주문이 생긴 공구를 잘못 알린다).
     */
    public List<Long> completeFinished(Instant now) {
        return jdbc.queryForList("UPDATE group_buys g SET status = 'COMPLETED', updated_at = ? WHERE ((g.status = 'CLOSED_SUCCESS' AND g.fulfillment = 'IMMEDIATE') "
                + "OR g.status = 'SHIPPING') AND NOT EXISTS (SELECT 1 FROM group_buy_participations p WHERE p.group_buy_id = g.id "
                + "AND p.status IN (" + ParticipationStatus.sqlList(ParticipationStatus.IN_PROGRESS) + ")) RETURNING g.id", Long.class, Timestamp.from(now));
    }

    /** 단품 판매 중지(공급 불가 — 결정 53, 새 선점을 막는다) */
    public void stopSelling(long skuId, long groupBuyId) {
        query.update(S).set(S.onSale, false).where(S.id.eq(skuId), S.groupBuyId.eq(groupBuyId)).execute();
    }

    /** 이 단품들 가운데 공급 불가로 표시된 것이 있나(결정 53 — 결제 확정이 단품 행 UPDATE 뒤에 묻는다. 공급 불가 기록은 같은 단품 행을 잠그는 트랜잭션에서 쓴다) */
    public boolean anySupplyFailed(long groupBuyId, Collection<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return false;
        }
        return query.selectOne().from(SF).where(SF.groupBuyId.eq(groupBuyId), SF.skuId.in(skuIds)).fetchFirst() != null;
    }

    /** 발주(마감 후 발주 공구 — CLOSED_SUCCESS → ORDERED, 공구 단위 10-3) */
    public boolean markOrdered(long id, Instant now) {
        return query.update(G).set(G.status, GroupBuyStatus.ORDERED).set(G.updatedAt, now)
                .where(G.id.eq(id), G.status.eq(GroupBuyStatus.CLOSED_SUCCESS), G.fulfillment.eq("AFTER_CLOSE")).execute() == 1;
    }

    /** 첫 발송 처리(ORDERED → SHIPPING — 결정 9) */
    public boolean markShipping(long id, Instant now) {
        return query.update(G).set(G.status, GroupBuyStatus.SHIPPING).set(G.updatedAt, now).where(G.id.eq(id), G.status.eq(GroupBuyStatus.ORDERED)).execute() == 1;
    }

    /** 공구 상태 이력 한 줄. JDBC 인 이유: 결제 확정 세이브포인트 안(다 팔림 마감)에서도 넣는다 — 그 안에서 JPA 문장이 실패하면 업무 트랜잭션 전체가 롤백 전용 */
    public void insertStatusHistory(long id, long groupBuyId, GroupBuyStatus from, GroupBuyStatus to, String reason, Long actorId, Instant now) {
        jdbc.update("INSERT INTO group_buy_status_history (id, group_buy_id, from_status, to_status, reason, actor_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id,
                groupBuyId, from == null ? null : from.name(), to.name(), reason, actorId, Timestamp.from(now));
    }

    // ── 공구 단품·옵션 축(결정 88·89) ───────────────────────────────────────

    /** 단품 — MAIN 먼저, 차례대로 */
    public List<SkuRow> skus(long groupBuyId) {
        return query.select(S.id, S.groupBuyId, S.kind, S.productSkuId, S.valueIds, S.name, S.extraPrice, S.addonPrice, S.maxQty, S.initialMaxQty, S.currentQty, S.reservedQty,
                S.onSale, S.supplyPrice, S.sortOrder).from(S).where(S.groupBuyId.eq(groupBuyId)).orderBy(S.kind.desc(), S.sortOrder.asc(), S.id.asc()).fetch().stream()
                .map(GroupBuyStore::sku).toList();
    }

    private static SkuRow sku(Tuple t) {
        return new SkuRow(t.get(S.id), t.get(S.groupBuyId), t.get(S.kind), t.get(S.productSkuId), list(t.get(S.valueIds)), t.get(S.name), n(t.get(S.extraPrice)),
                t.get(S.addonPrice), t.get(S.maxQty), t.get(S.initialMaxQty), n(t.get(S.currentQty)), n(t.get(S.reservedQty)), Boolean.TRUE.equals(t.get(S.onSale)),
                t.get(S.supplyPrice), n(t.get(S.sortOrder)));
    }

    /** 공구 상품의 옵션 축과 값(차례대로) */
    public List<AxisRow> axes(long groupBuyId) {
        Long productId = query.select(G.productId).from(G).where(G.id.eq(groupBuyId)).fetchFirst();
        return productId == null ? List.of() : axesOfProduct(query, productId);
    }

    /** 상품의 옵션 축(옵션 종류 — 이름은 종류의 지금 이름)과 값(차례대로) — 축 한 번, 값 한 번(축마다 따로 읽지 않는다) */
    static List<AxisRow> axesOfProduct(JPAQueryFactory query, long productId) {
        List<AxisRow> axes = query.select(A.id, A.optionTypeCode, T.name, A.sortOrder).from(A).join(T).on(T.code.eq(A.optionTypeCode)).where(A.productId.eq(productId))
                .orderBy(A.sortOrder.asc(), A.id.asc()).fetch().stream()
                .map(t -> new AxisRow(t.get(A.id), t.get(A.optionTypeCode), t.get(T.name), n(t.get(A.sortOrder)), new ArrayList<>())).toList();
        if (axes.isEmpty()) {
            return axes;
        }
        Map<Long, AxisRow> byId = new LinkedHashMap<>();
        axes.forEach(a -> byId.put(a.id(), a));
        query.select(V.id, V.axisId, V.value, V.sortOrder).from(V).where(V.axisId.in(byId.keySet())).orderBy(V.sortOrder.asc(), V.id.asc()).fetch()
                .forEach(t -> byId.get(t.get(V.axisId)).values().add(new ValueRow(t.get(V.id), t.get(V.axisId), t.get(V.value), n(t.get(V.sortOrder)))));
        return axes;
    }

    /** 새 공구 단품(MAIN 은 추가금, ADDON 은 자기 가격) */
    public void insertSku(long id, long groupBuyId, SkuKind kind, long productSkuId, List<Long> valueIds, String name, long extraPrice, Long addonPrice, Integer maxQty,
                          boolean onSale, Long supplyPrice, int sortOrder) {
        insert(GroupBuySkuEntity.create(id, groupBuyId, kind, productSkuId, array(valueIds), name, extraPrice, addonPrice, maxQty, onSale, supplyPrice, sortOrder));
    }

    /** DRAFT 의 공구 단품을 모두 지운다(다시 넣는다) */
    public void deleteSkus(long groupBuyId) {
        query.delete(S).where(S.groupBuyId.eq(groupBuyId)).execute();
    }

    /** 공급가를 고친다(모집 뒤에도 — 결정 89) */
    public void updateSupplyPrice(long skuId, long groupBuyId, Long supplyPrice) {
        query.update(S).set(S.supplyPrice, supplyPrice).where(S.id.eq(skuId), S.groupBuyId.eq(groupBuyId)).execute();
    }

    // ── 수정 이력 ───────────────────────────────────────────────────────────

    /**
     * 지금 공구 상태의 사본을 수정 이력으로 남긴다(표시 기록) — 상품 내용(이름·설명·이미지·옵션 축·고시 — 공구 상품과 추가 구성상품의 상품, 결정 89)·공구 단품(공급가 빼고)·수량.
     * Native SQL 인 이유: jsonb_agg·jsonb_build_object·jsonb_object_agg 로 여러 표의 사본을 jsonb 칸에 한 문장 INSERT … SELECT 로 담는다.
     */
    public void snapshotRevision(long id, long groupBuyId, int revisionNo, String changeKind, Long editedBy, Instant now) {
        jdbc.update("INSERT INTO group_buy_revisions (id, group_buy_id, revision_no, title, product_name, summary, spec, description, images, max_qty, added_qty, "
                        + "option_axes, skus, notices, change_kind, edited_by, created_at) SELECT ?, g.id, ?, g.title, p.name, p.summary, p.spec, p.description, "
                        + "coalesce((SELECT jsonb_agg(jsonb_build_object('imageKey', i.image_key, 'thumbKey', i.thumb_key) ORDER BY i.sort_order) FROM group_buy_product_images i "
                        + "WHERE i.product_id = p.id), '[]'::jsonb), g.max_qty, g.added_qty, "
                        + "coalesce((SELECT jsonb_agg(jsonb_build_object('code', a.option_type_code, 'name', (SELECT t.name FROM group_buy_option_types t WHERE t.code = a.option_type_code), 'values', (SELECT coalesce(jsonb_agg(v.value ORDER BY v.sort_order, v.id), '[]'::jsonb) "
                        + "FROM group_buy_option_values v WHERE v.axis_id = a.id)) ORDER BY a.sort_order, a.id) FROM group_buy_option_axes a WHERE a.product_id = p.id), '[]'::jsonb), "
                        + "coalesce((SELECT jsonb_agg(jsonb_build_object('skuId', s.id::text, 'productSkuId', s.product_sku_id::text, 'kind', s.kind, 'name', s.name, "
                        + "'extraPrice', s.extra_price, 'addonPrice', s.addon_price, 'maxQty', s.max_qty, 'onSale', s.on_sale) ORDER BY s.kind DESC, s.sort_order, s.id) "
                        + "FROM group_buy_skus s WHERE s.group_buy_id = g.id), '[]'::jsonb), "
                        + "coalesce((SELECT jsonb_agg(jsonb_build_object('productId', n.product_id::text, 'title', n.title, 'categoryCode', n.category_code, "
                        + "'catalogVersion', n.catalog_version, "
                        + "'skus', (SELECT coalesce(jsonb_agg(ps.name ORDER BY ps.sort_order, ps.id), '[]'::jsonb) FROM group_buy_notice_skus x "
                        + "JOIN group_buy_product_skus ps ON ps.id = x.product_sku_id WHERE x.notice_id = n.id), "
                        + "'values', (SELECT coalesce(jsonb_object_agg(v.item_key, jsonb_build_object('value', v.value, 'unavailable', v.unavailable, "
                        + "'reason', v.unavailable_reason)), '{}'::jsonb) FROM group_buy_notice_values v WHERE v.notice_id = n.id AND NOT v.custom), "
                        + "'customItems', (SELECT coalesce(jsonb_agg(jsonb_build_object('name', v.custom_name, 'value', v.value) ORDER BY v.sort_order), '[]'::jsonb) "
                        + "FROM group_buy_notice_values v WHERE v.notice_id = n.id AND v.custom)) ORDER BY n.product_id = p.id DESC, n.product_id, n.sort_order) "
                        + "FROM group_buy_notices n WHERE n.product_id = p.id OR n.product_id IN (SELECT ps.product_id FROM group_buy_skus s "
                        + "JOIN group_buy_product_skus ps ON ps.id = s.product_sku_id WHERE s.group_buy_id = g.id AND s.kind = 'ADDON')), '[]'::jsonb), ?, ?, ? "
                        + "FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id WHERE g.id = ?", id, revisionNo, changeKind, editedBy, Timestamp.from(now),
                groupBuyId);
    }

    // ── 차수 ────────────────────────────────────────────────────────────────

    /** 루트에 DRAFT·RECRUITING 차수가 있는가(결정 31) */
    public boolean roundOpen(long roundRootId) {
        return query.selectOne().from(G).where(G.roundRootId.eq(roundRootId), G.status.in(GroupBuyStatus.DRAFT, GroupBuyStatus.RECRUITING)).fetchFirst() != null;
    }

    public int maxRoundNo(long roundRootId) {
        return n(query.select(G.roundNo.max()).from(G).where(G.roundRootId.eq(roundRootId)).fetchOne());
    }

    /** 루트의 모든 차수 ID(차수 사이 1인 한도 합산 — 결정 24·86) */
    public List<Long> roundIds(long roundRootId) {
        return query.select(G.id).from(G).where(G.roundRootId.eq(roundRootId)).orderBy(G.roundNo.asc()).fetch();
    }

    /** 새 차수 모집 시작 때 알릴 회원 — 루트의 모든 차수의 NEXT_ROUND·RESTOCK·CLOSING_SOON 신청자, 같은 회원 한 번(결정 31) */
    public List<Long> roundWatchers(long roundRootId, long exceptGroupBuyId) {
        return query.select(W.userId).distinct().from(W).join(G).on(G.id.eq(W.groupBuyId))
                .where(G.roundRootId.eq(roundRootId), G.id.ne(exceptGroupBuyId), W.kind.in(WatchKind.NEXT_ROUND, WatchKind.RESTOCK, WatchKind.CLOSING_SOON))
                .orderBy(W.userId.asc()).fetch();
    }

    // ── 지연 공지 ───────────────────────────────────────────────────────────

    public void insertDelayNotice(long id, long groupBuyId, LocalDate previous, LocalDate next, String reason, Long operatorId, Instant now) {
        insert(GroupBuyDelayNoticeEntity.create(id, groupBuyId, previous, next, reason, operatorId, now));
    }

    /** 발송 예정일을 바꾼다(지연 공지 — 모집 뒤에도 이 길로만) */
    public void changeShipPlan(long groupBuyId, LocalDate next, Instant now) {
        query.update(G).set(G.shipPlannedAt, next).set(G.updatedAt, now).where(G.id.eq(groupBuyId)).execute();
    }

    /** 그 공구의 가장 이른 지연 공지 시각(없으면 빈 값) — 그 뒤로 발송 전 주문은 승인 없이 전액 취소할 수 있다(결정 52) */
    public Optional<Instant> firstDelayNoticeAt(long groupBuyId) {
        return Optional.ofNullable(query.select(D.createdAt.min()).from(D).where(D.groupBuyId.eq(groupBuyId)).fetchOne());
    }
}
