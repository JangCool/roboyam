package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.QGroupBuyEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationEntity;
import com.community.groupbuy.entity.QGroupBuyParticipationItemEntity;
import com.community.groupbuy.entity.QGroupBuyProductEntity;
import com.community.groupbuy.entity.QGroupBuyRefundEntity;
import com.community.groupbuy.entity.QGroupBuySkuEntity;
import com.community.groupbuy.entity.QGroupBuyWatchEntity;
import com.community.groupbuy.entity.SkuKind;
import com.community.groupbuy.entity.WatchKind;
import com.community.site.entity.QSiteSettingEntity;
import com.community.site.entity.SiteSettingEntity;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 공구 알림({@code GroupBuyNotifications})이 읽는 값 — 알림에 쓰는 주문 한 줄·공구 제목·수량 풀림 판단·관심 신청자·계약 내용 통지의 판매 조건. 서비스 안에 있던 SQL 을 옮겼다.
 *
 * <p>QueryDSL 이 기본이다({@link GroupBuyStoreSupport}). Native SQL 로 남긴 것과 이유: 관심 신청에 알린 시각을 찍는 두 문장({@link #claimRestockWatchers} ·
 * {@link #claimClosingSoon} — 일괄 {@code UPDATE … RETURNING}: 10분·한 번 조건을 이긴 신청만 같은 문장에서 받아 그 회원에게만 알린다), 결제 표({@code payment_orders})를
 * 읽는 {@link #paymentAmount} — 결제 엔티티는 3단계 2차에서 만든다.
 */
@Repository
public class GroupBuyNotificationStore extends GroupBuyStoreSupport {

    private static final QGroupBuyParticipationEntity PA = QGroupBuyParticipationEntity.groupBuyParticipationEntity;
    private static final QGroupBuyEntity G = QGroupBuyEntity.groupBuyEntity;
    private static final QGroupBuyProductEntity P = QGroupBuyProductEntity.groupBuyProductEntity;
    private static final QGroupBuySkuEntity S = QGroupBuySkuEntity.groupBuySkuEntity;
    private static final QGroupBuyParticipationItemEntity I = QGroupBuyParticipationItemEntity.groupBuyParticipationItemEntity;
    private static final QGroupBuyWatchEntity W = QGroupBuyWatchEntity.groupBuyWatchEntity;
    private static final QGroupBuyRefundEntity R = QGroupBuyRefundEntity.groupBuyRefundEntity;
    private static final QSiteSettingEntity SITE = QSiteSettingEntity.siteSettingEntity;

    /** 알림에 쓰는 주문 한 줄 — {@code title} 공구 제목(비었으면 상품 이름) */
    public record OrderRow(long id, String orderNo, long groupBuyId, long userId, long amount, long shippingFee, String title, ParticipationStatus status) {
    }

    /** 수량 풀림 판단 — 공구 상태·남은 수량(MAIN)·제목 */
    public record QtyState(GroupBuyStatus status, long remaining, String title) {
    }

    /** 수량 풀림 판단 — 단품 */
    public record SkuState(SkuKind kind, Integer maxQty, int currentQty, int reservedQty, boolean onSale) {
    }

    /** 마감 임박으로 알린 신청 한 줄 */
    public record ClosingSoonRow(long userId, long groupBuyId, Instant deadline) {
    }

    /** 회원 한 명의 가장 최근 진행 중 주문 번호 */
    public record UserOrder(long userId, String orderNo) {
    }

    /** 환불 기록 — 알림 몫 */
    public record RefundBrief(long participationId, long amount, String method) {
    }

    /** 계약 내용 통지의 판매 조건(결정 57) */
    public record Terms(Instant startAt, Instant endAt, int maxQty, Integer maxQtyPerUser, int roundNo, int minQty, LocalDate shipPlannedAt, Integer shipWithinBusinessDays,
                        String withdrawalRestriction) {
    }

    public GroupBuyNotificationStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    /** {@code COALESCE(NULLIF(g.title, ''), p.name)} */
    private static String title(String groupTitle, String productName) {
        return groupTitle == null || groupTitle.isEmpty() ? productName : groupTitle;
    }

    // ── 주문 ────────────────────────────────────────────────────────────────

    private JPAQuery<Tuple> orders() {
        return query.select(PA.id, PA.orderNo, PA.groupBuyId, PA.userId, PA.amount, PA.shippingFee, G.title, P.name, PA.status).from(PA).join(G).on(G.id.eq(PA.groupBuyId))
                .join(P).on(P.id.eq(G.productId));
    }

    private static OrderRow order(Tuple t) {
        return new OrderRow(t.get(PA.id), t.get(PA.orderNo), t.get(PA.groupBuyId), t.get(PA.userId), n(t.get(PA.amount)), n(t.get(PA.shippingFee)),
                title(t.get(G.title), t.get(P.name)), t.get(PA.status));
    }

    public Optional<OrderRow> order(long participationId) {
        return Optional.ofNullable(orders().where(PA.id.eq(participationId)).fetchFirst()).map(GroupBuyNotificationStore::order);
    }

    /** 그 공구에서 그 상태의 주문(차례 없음 — 예전 문장과 같다) */
    public List<OrderRow> ordersOfGroup(long groupBuyId, Set<ParticipationStatus> statuses) {
        return orders().where(PA.groupBuyId.eq(groupBuyId), PA.status.in(statuses)).fetch().stream().map(GroupBuyNotificationStore::order).toList();
    }

    /** 진행 중 주문이 있는 회원마다 가장 최근 주문 번호(회원 차례) — 예전 {@code DISTINCT ON (user_id) … ORDER BY user_id, created_at DESC} 와 같은 결과 */
    public List<UserOrder> latestInProgressOrderPerUser(long groupBuyId) {
        Map<Long, String> byUser = new LinkedHashMap<>();
        query.select(PA.userId, PA.orderNo).from(PA).where(PA.groupBuyId.eq(groupBuyId), PA.status.in(ParticipationStatus.IN_PROGRESS))
                .orderBy(PA.userId.asc(), PA.createdAt.desc()).fetch().forEach(t -> byUser.putIfAbsent(t.get(PA.userId), t.get(PA.orderNo)));
        List<UserOrder> out = new ArrayList<>();
        byUser.forEach((user, orderNo) -> out.add(new UserOrder(user, orderNo)));
        return out;
    }

    /** 주문 줄 「이름 × 수량」(MAIN 먼저, 단품 차례) */
    public List<String> itemLines(long participationId) {
        return query.select(S.name, I.qty).from(I).join(S).on(S.id.eq(I.skuId)).where(I.participationId.eq(participationId))
                .orderBy(S.kind.desc(), S.sortOrder.asc(), I.id.asc()).fetch().stream().map(t -> t.get(S.name) + " × " + n(t.get(I.qty))).toList();
    }

    // ── 공구 ────────────────────────────────────────────────────────────────

    /** 공구 제목 = 공구 이름(없으면 상품 이름). 공구가 없으면 빈 글자 */
    public String title(long groupBuyId) {
        Tuple t = query.select(G.title, P.name).from(G).join(P).on(P.id.eq(G.productId)).where(G.id.eq(groupBuyId)).fetchFirst();
        if (t == null) {
            return "";
        }
        String title = title(t.get(G.title), t.get(P.name));
        return title == null ? "" : title;
    }

    public Optional<QtyState> qtyState(long groupBuyId) {
        Tuple t = query.select(G.status, G.maxQty, G.currentQty, G.reservedQty, G.title, P.name).from(G).join(P).on(P.id.eq(G.productId)).where(G.id.eq(groupBuyId))
                .fetchFirst();
        return t == null ? Optional.empty()
                : Optional.of(new QtyState(t.get(G.status), (long) n(t.get(G.maxQty)) - n(t.get(G.currentQty)) - n(t.get(G.reservedQty)), title(t.get(G.title), t.get(P.name))));
    }

    public Optional<SkuState> skuState(long skuId, long groupBuyId) {
        Tuple t = query.select(S.kind, S.maxQty, S.currentQty, S.reservedQty, S.onSale).from(S).where(S.id.eq(skuId), S.groupBuyId.eq(groupBuyId)).fetchFirst();
        return t == null ? Optional.empty()
                : Optional.of(new SkuState(t.get(S.kind), t.get(S.maxQty), n(t.get(S.currentQty)), n(t.get(S.reservedQty)), Boolean.TRUE.equals(t.get(S.onSale))));
    }

    public Optional<Terms> terms(long groupBuyId) {
        Tuple t = query.select(G.startAt, G.endAt, G.maxQty, G.maxQtyPerUser, G.roundNo, G.minQty, G.shipPlannedAt, G.shipWithinBusinessDays, P.withdrawalRestriction)
                .from(G).join(P).on(P.id.eq(G.productId)).where(G.id.eq(groupBuyId)).fetchFirst();
        return t == null ? Optional.empty()
                : Optional.of(new Terms(t.get(G.startAt), t.get(G.endAt), n(t.get(G.maxQty)), t.get(G.maxQtyPerUser), n(t.get(G.roundNo)), n(t.get(G.minQty)),
                        t.get(G.shipPlannedAt), t.get(G.shipWithinBusinessDays), t.get(P.withdrawalRestriction)));
    }

    // ── 관심 신청 ───────────────────────────────────────────────────────────

    /**
     * 수량 풀림 신청에 알린 시각을 찍고 그 회원을 받는다 — {@code skuId} 가 있으면 그 단품 신청, 없으면 공구 신청. 같은 신청은 {@code notifiedBefore} 뒤로 한 번(10분).
     * Native SQL 인 이유: 일괄 {@code UPDATE … RETURNING user_id} — 조건을 이긴 신청만 같은 문장에서 받는다.
     */
    public List<Long> claimRestockWatchers(long groupBuyId, Long skuId, Instant now, Instant notifiedBefore) {
        if (skuId != null) {
            return jdbc.queryForList("UPDATE group_buy_watches SET last_notified_at = ? WHERE group_buy_id = ? AND kind = 'RESTOCK' AND sku_id = ? "
                    + "AND (last_notified_at IS NULL OR last_notified_at <= ?) RETURNING user_id", Long.class, Timestamp.from(now), groupBuyId, skuId, Timestamp.from(notifiedBefore));
        }
        return jdbc.queryForList("UPDATE group_buy_watches SET last_notified_at = ? WHERE group_buy_id = ? AND kind = 'RESTOCK' AND sku_id IS NULL "
                + "AND (last_notified_at IS NULL OR last_notified_at <= ?) RETURNING user_id", Long.class, Timestamp.from(now), groupBuyId, Timestamp.from(notifiedBefore));
    }

    /** 그 공구의 그 종류 신청자(같은 회원 한 번) */
    public List<Long> watchers(long groupBuyId, Collection<WatchKind> kinds) {
        return query.select(W.userId).distinct().from(W).where(W.groupBuyId.eq(groupBuyId), W.kind.in(kinds)).fetch();
    }

    /**
     * 마감이 {@code (now, until]} 인 모집 중 공구의 아직 알리지 않은 CLOSING_SOON 신청에 알린 시각을 찍고 그 줄을 받는다.
     * Native SQL 인 이유: 일괄 {@code UPDATE … FROM group_buys … RETURNING} — 한 번만 조건을 이긴 신청(서버 여러 대)을 같은 문장에서 받는다.
     */
    public List<ClosingSoonRow> claimClosingSoon(Instant now, Instant until) {
        return jdbc.query("UPDATE group_buy_watches w SET last_notified_at = ? FROM group_buys g WHERE g.id = w.group_buy_id "
                        + "AND w.kind = 'CLOSING_SOON' AND w.last_notified_at IS NULL AND g.status = 'RECRUITING' AND COALESCE(g.extended_until, g.end_at) <= ? "
                        + "AND COALESCE(g.extended_until, g.end_at) > ? RETURNING w.user_id, g.id, COALESCE(g.extended_until, g.end_at)",
                (rs, i) -> new ClosingSoonRow(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant()), Timestamp.from(now), Timestamp.from(until), Timestamp.from(now));
    }

    // ── 환불·결제·사이트 ────────────────────────────────────────────────────

    public Optional<RefundBrief> refund(long refundId) {
        Tuple t = query.select(R.participationId, R.amount, R.method).from(R).where(R.id.eq(refundId)).fetchFirst();
        return t == null ? Optional.empty() : Optional.of(new RefundBrief(t.get(R.participationId), n(t.get(R.amount)), t.get(R.method)));
    }

    /** 결제 주문 금액(없으면 0). JDBC 인 이유: 결제 표({@code payment_orders}) — 결제 엔티티는 다른 작업자가 만드는 중이라 3단계 2차에서 결제 엔티티로 바꾼다 */
    public long paymentAmount(long paymentOrderId) {
        return jdbc.query("SELECT amount FROM payment_orders WHERE id = ?", (rs, i) -> rs.getLong(1), paymentOrderId).stream().findFirst().orElse(0L);
    }

    /** 사이트 공개 주소(없으면 null) */
    public String sitePublicUrl() {
        return query.select(SITE.publicUrl).from(SITE).where(SITE.id.eq(SiteSettingEntity.ROW_ID)).fetchFirst();
    }
}
