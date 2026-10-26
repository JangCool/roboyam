package com.community.pointshop.service;

import com.community.point.entity.PointReason;
import com.community.point.service.PointService;
import com.community.pointshop.dto.PointShopBuyer;
import com.community.pointshop.dto.request.PointShopPurchaseQuery;
import com.community.pointshop.dto.response.PointShopPurchaseMemberView;
import com.community.pointshop.dto.response.PointShopPurchasePageView;
import com.community.pointshop.dto.response.PointShopPurchaseRefundView;
import com.community.pointshop.dto.response.PointShopPurchaseResultView;
import com.community.pointshop.dto.response.PointShopPurchaseRowView;
import com.community.pointshop.dto.response.PointShopRefundRequestView;
import com.community.pointshop.dto.response.PointShopSelfRefundView;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopRefundKind;
import com.community.pointshop.entity.PointShopSaleState;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.entity.UserItemStatus;
import com.community.pointshop.exception.PointShopAlreadyOwnedException;
import com.community.pointshop.exception.PointShopBlockedException;
import com.community.pointshop.exception.PointShopItemNotFoundException;
import com.community.pointshop.exception.PointShopNotOnSaleException;
import com.community.pointshop.exception.PointShopSoldOutException;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopItemStore.ItemRow;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.repository.master.PointShopMemberStore.MemberState;
import com.community.pointshop.repository.master.PointShopPurchaseStore;
import com.community.pointshop.repository.master.PointShopPurchaseStore.AdminQuery;
import com.community.pointshop.repository.master.PointShopPurchaseStore.AdminRow;
import com.community.pointshop.repository.master.PointShopPurchaseStore.PurchaseRow;
import com.community.pointshop.repository.master.PointShopPurchaseStore.RequestRow;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.pointshop.repository.master.UserItemStore.UserItemRow;
import com.community.restriction.entity.RestrictedFeature;
import com.community.restriction.service.MemberRestrictionService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 상점 구매(commerce.md 7-2, 계약 「B」 구매) — <b>한 트랜잭션</b>. 멱등 키·요청 제한({@code POINT_SHOP_PURCHASE} 1분 10번)은 api 의 {@code MoneyRequestGuard} 가 이 메서드를 감싼다.
 *
 * <ol>
 *   <li><b>자격</b>: 회원(없거나 탈퇴 401) → 정지·영구 정지 {@code 403 ACCOUNT_RESTRICTED} → 이메일 인증 전 {@code 403 EMAIL_NOT_VERIFIED} → 회원 권한 {@code pointshop.use} 없음
 *       {@code 403 PERMISSION_DENIED} → 포인트 동결 {@code 403 MEMBER_RESTRICTED}(기능 제한 {@code POINT_USE}).</li>
 *   <li>고지 동의 없음 {@code 400 VALIDATION fields.agreeRefundNotice} → 품목 없음 {@code 404} → 판매 기간 밖·사용 안 함 {@code 409 POINT_SHOP_NOT_ON_SALE} · 재고 0 {@code 409 POINT_SHOP_SOLD_OUT}
 *       → 영구 아이템을 이미 가짐 {@code 409 POINT_SHOP_ALREADY_OWNED}.</li>
 *   <li><b>재고 조건부 차감</b>(한정 재고만 — 마지막 하나를 둘이 사면 하나만, 진 쪽은 {@code SOLD_OUT}).</li>
 *   <li><b>포인트 사용</b>({@link PointService#use} — 사유 {@code POINT_SHOP_PURCHASE}, 가격은 서버가 품목에서 읽은 값, 적립(무상) 소멸 임박순 → 충전(유상) 순, 결정 9) — 업무 잠금 뒤 <b>마지막 잠금</b>.
 *       모자라면 {@code 409 POINT_INSUFFICIENT}(트랜잭션째 되돌려 재고도 그대로).</li>
 *   <li>구매 줄(품목 사본·고지 동의 시각) + 보유 줄. 기간제를 기간 중에 또 사면 앞 끝에서 이어 붙는다(잔액 잠금 뒤에 끝을 읽어 같은 회원의 동시 구매도 차례대로 이어진다).
 *       영구 아이템이 동시에 두 번 들어오면 부분 유일 색인이 막는다 → {@code ALREADY_OWNED}(트랜잭션째 되돌림).</li>
 * </ol>
 * 사도 아바타 프로필은 바뀌지 않는다(화면이 「바로 쓰기」로 이어 부른다).
 */
@Service
public class PointShopPurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PointShopPurchaseService.class);

    private final PointShopItemStore items;
    private final PointShopPurchaseStore purchases;
    private final UserItemStore userItems;
    private final PointShopMemberStore members;
    private final PointShopCatalogService catalog;
    private final PointService points;
    private final MemberRestrictionService restrictions;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public PointShopPurchaseService(PointShopItemStore items, PointShopPurchaseStore purchases, UserItemStore userItems, PointShopMemberStore members,
                                    PointShopCatalogService catalog, PointService points, MemberRestrictionService restrictions, SnowflakeIdGenerator ids,
                                    ObjectProvider<Clock> clocks) {
        this.items = items;
        this.purchases = purchases;
        this.userItems = userItems;
        this.members = members;
        this.catalog = catalog;
        this.points = points;
        this.restrictions = restrictions;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 품목 하나를 산다 — {@code POST /api/point-shop/purchases { itemId, agreeRefundNotice }}.
     *
     * @return 구매·보유 줄 ID, 산 뒤 내 포인트, 산 뒤의 품목
     */
    @Transactional
    public PointShopPurchaseResultView purchase(PointShopBuyer buyer, long itemId, boolean agreeRefundNotice) {
        long userId = buyer.userId();
        assertCanBuy(buyer);
        if (!agreeRefundNotice) {
            throw ValidationException.of("agreeRefundNotice", new MessageRef("pointshop.purchase.agreeRefundNotice.required"));
        }
        Instant now = now();
        ItemRow item = items.find(itemId).orElseThrow(PointShopItemNotFoundException::new);
        PointShopSaleState state = PointShopCatalogService.saleState(item, now);
        if (state == PointShopSaleState.UPCOMING || state == PointShopSaleState.ENDED) {
            throw new PointShopNotOnSaleException();
        }
        if (state == PointShopSaleState.SOLD_OUT) {
            throw new PointShopSoldOutException();
        }
        if (item.usageType() == PointShopUsageType.PERMANENT && userItems.ownsPermanent(userId, itemId)) {
            throw new PointShopAlreadyOwnedException();
        }
        if (!items.takeStock(itemId)) {
            throw new PointShopSoldOutException();
        }

        long purchaseId = ids.nextId();
        long useLedgerId = points.use(userId, item.price(), PointReason.POINT_SHOP_PURCHASE, PointService.REF_POINT_SHOP_PURCHASE, purchaseId);
        purchases.insert(new PurchaseRow(purchaseId, userId, itemId, item.code(), item.name(), item.kind(), item.usageType(), item.price(), item.durationDays(),
                PointShopPurchaseStatus.COMPLETED, now, useLedgerId, null, null, null, null, now, null));

        Instant startsAt = now;
        Instant expiresAt = null;
        if (item.usageType() == PointShopUsageType.PERIOD) {
            startsAt = userItems.periodEnd(userId, itemId, now).orElse(now);
            expiresAt = startsAt.plus(Duration.ofDays(item.durationDays()));
        }
        long userItemId = ids.nextId();
        try {
            userItems.insert(new UserItemRow(userItemId, userId, itemId, purchaseId, item.usageType(), UserItemStatus.OWNED, startsAt, expiresAt, null, null, null, now));
        } catch (DuplicateKeyException e) {
            throw new PointShopAlreadyOwnedException(); // 같은 영구 품목을 동시에 두 번 — 부분 유일 색인이 막았다(트랜잭션째 되돌린다)
        }
        log.info("포인트 상점 구매 userId={} itemId={} purchaseId={} price={}", userId, itemId, purchaseId, item.price());
        ItemRow after = items.find(itemId).orElseThrow();
        return new PointShopPurchaseResultView(Long.toString(purchaseId), Long.toString(userItemId), members.balance(userId), catalog.memberItemView(userId, after, now));
    }

    /** 자격 — 계약 순서(401 → 정지 → 이메일 인증 → 회원 권한 → 포인트 동결) */
    private void assertCanBuy(PointShopBuyer buyer) {
        MemberState state = members.state(buyer.userId()).filter(s -> !s.gone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (state.suspended()) {
            throw new PointShopBlockedException(PointShopBlockedException.ACCOUNT_RESTRICTED);
        }
        if (!state.emailVerified()) {
            throw new PointShopBlockedException(PointShopBlockedException.EMAIL_NOT_VERIFIED);
        }
        if (!buyer.pointShopPermitted()) {
            throw new PointShopBlockedException(PointShopBlockedException.PERMISSION_DENIED);
        }
        restrictions.assertAllowed(buyer.userId(), RestrictedFeature.POINT_USE);
    }

    // ── 관리자 구매 내역 ────────────────────────────────────────────────────

    /**
     * 관리자 구매 내역 — {@code GET /api/admin/point-shop/purchases}(권한 {@code pointshop.read}). 최신 구매가 위. 셀프 환불 판정은 <b>지금</b> 설정의 기한으로.
     *
     * @throws ValidationException {@code fields.page}(0 이상)·{@code fields.size}(1~100)·{@code fields.to}(끝이 시작보다 앞)
     */
    @Transactional(readOnly = true)
    public PointShopPurchasePageView adminPurchases(PointShopPurchaseQuery query) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        if (query.page() < 0) {
            bad.put("page", new MessageRef("pointshop.purchases.page.min"));
        }
        if (query.size() < 1 || query.size() > PointShopPurchaseQuery.SIZE_MAX) {
            bad.put("size", new MessageRef("pointshop.purchases.size.range", 1, PointShopPurchaseQuery.SIZE_MAX));
        }
        if (query.from() != null && query.to() != null && !query.to().isAfter(query.from())) {
            bad.put("to", new MessageRef("pointshop.purchases.period.order"));
        }
        if (!bad.isEmpty()) {
            throw new ValidationException(bad);
        }
        AdminQuery q = new AdminQuery(query.from(), query.to(), query.status(), query.kind(), query.q(), query.page(), query.size(), query.refundRequest());
        Instant now = now();
        int refundDays = items.refundDays();
        List<AdminRow> page = purchases.adminPage(q);
        Map<Long, RequestRow> requests = purchases.latestRequests(page.stream().map(row -> row.purchase().id()).toList());
        List<PointShopPurchaseRowView> rows = page.stream().map(row -> adminRow(row, requests.get(row.purchase().id()), refundDays, now)).toList();
        return new PointShopPurchasePageView(rows, purchases.adminCount(q), query.page(), query.size());
    }

    private static PointShopPurchaseRowView adminRow(AdminRow row, RequestRow request, int refundDays, Instant now) {
        PurchaseRow p = row.purchase();
        PointShopPurchaseMemberView member = new PointShopPurchaseMemberView(row.withdrawn() ? null : Long.toString(p.userId()), row.nickname(), row.withdrawn());
        PointShopSelfRefundView self = null;
        PointShopPurchaseRefundView refund = null;
        if (p.status() == PointShopPurchaseStatus.COMPLETED) {
            String blocked = PointShopRules.selfRefundBlock(UserItemStatus.OWNED, row.firstUsedAt(), p.createdAt(), refundDays, now);
            self = new PointShopSelfRefundView(blocked == null, PointShopRules.refundUntil(p.createdAt(), refundDays), blocked);
        } else {
            PointShopRefundKind kind = p.refundKind() != null ? p.refundKind() : p.refundedByOperatorId() != null ? PointShopRefundKind.EXCEPTION : PointShopRefundKind.SELF;
            refund = new PointShopPurchaseRefundView(kind.name(), p.refundedAt(), kind == PointShopRefundKind.SELF ? null : row.operatorName(), p.refundReason(), p.price());
        }
        return new PointShopPurchaseRowView(Long.toString(p.id()), p.createdAt(), member, Long.toString(p.itemId()), p.itemCode(), p.itemName(), row.avatarId(),
                p.itemKind(), p.usageType(), p.price(), p.durationDays(), p.status(), row.firstUsedAt(), self, refund, requestView(request));
    }

    /** 환불 신청 칸(없으면 null) — 내 아이템·관리자 구매 줄이 같이 쓴다 */
    static PointShopRefundRequestView requestView(RequestRow request) {
        return request == null ? null : new PointShopRefundRequestView(Long.toString(request.id()), request.status(), request.requestedAt(), request.decidedAt(),
                request.rejectReason());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
