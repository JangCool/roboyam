package com.community.pointshop.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.point.entity.PointReason;
import com.community.point.service.PointService;
import com.community.pointshop.dto.SelfRefundOutcome;
import com.community.pointshop.dto.response.MyItemRefundView;
import com.community.pointshop.dto.response.PointShopOperatorRefundView;
import com.community.pointshop.dto.response.PointShopRefundDecisionView;
import com.community.pointshop.dto.response.PointShopRefundRequestedView;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopRefundKind;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import com.community.pointshop.entity.UserItemStatus;
import com.community.pointshop.exception.PointShopAlreadyRefundedException;
import com.community.pointshop.exception.PointShopPurchaseNotFoundException;
import com.community.pointshop.exception.PointShopRefundNotAllowedException;
import com.community.pointshop.exception.PointShopRefundPendingException;
import com.community.pointshop.exception.PointShopRefundRejectedException;
import com.community.pointshop.exception.PointShopRefundRequestNotFoundException;
import com.community.pointshop.exception.PointShopRefundRequestNotPendingException;
import com.community.pointshop.exception.UserItemNotFoundException;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopItemStore.ItemRow;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.repository.master.PointShopPurchaseStore;
import com.community.pointshop.repository.master.PointShopPurchaseStore.PurchaseRow;
import com.community.pointshop.repository.master.PointShopPurchaseStore.RequestRow;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.pointshop.repository.master.UserItemStore.UserItemRow;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 상점 환불(commerce.md 7-3 D6, 계약 「B」 환불·결정 1·「1-2 정지 중 환불 신청」) — 모든 품목이 같은 규칙(닉네임 변경권 포함). 한 트랜잭션.
 *
 * <ul>
 *   <li><b>셀프 환불</b>({@link #refundSelf}): 안 썼고(장착·사용·프로필로 고른 적 없음) 산 뒤 <b>지금 설정</b>의 {@code refund_days} 안. 아니면 {@code 409 POINT_SHOP_REFUND_NOT_ALLOWED}
 *       (+ {@code blockedBy}). 멱등 키·요청 제한({@code REFUND_REQUEST})은 api 의 {@code MoneyRequestGuard}.
 *       <b>정지·영구 정지 중</b>이면 바로 환불하지 않고 <b>환불 신청</b>(PENDING)을 만든다 — 조건은 신청 순간에 판정하고 고정한다(기다리는 동안 기한이 지나도 된다).
 *       거절된 구매는 정지 중 다시 신청할 수 없다({@code 409 POINT_SHOP_REFUND_REJECTED}) — 정지가 풀리면 기한 안 셀프 환불은 된다. 새 신청은 운영자 알림
 *       {@code POINT_SHOP_REFUND_REQUESTED}.</li>
 *   <li><b>대기 중</b>인 구매는 셀프 환불·다시 신청·예외 환불 모두 {@code 409 POINT_SHOP_REFUND_PENDING}(정지가 풀려도 — 수락·거절로 끝낸다). 장착·고르기·변경권 사용도 막는다(다른 서비스).</li>
 *   <li><b>신청 수락</b>({@link #approveRequest}, {@code pointshop.refund}): 조건을 다시 묻지 않고 기존 환불 길로 처리(환불 종류 {@code REQUEST}·처리 운영자), 감사 {@code POINT_SHOP_REFUND}(돈 관련)
 *       + 회원 알림 {@code POINT_SHOP_REFUND_APPROVED}. <b>거절</b>({@link #rejectRequest}): 사유 5~200자, 아이템은 다시 쓸 수 있다, 감사 {@code POINT_SHOP_REFUND_REJECT}(돈 관련)
 *       + 회원 알림 {@code POINT_SHOP_REFUND_REJECTED}(사유). 이미 결정된 신청은 {@code 409 POINT_SHOP_REFUND_REQUEST_NOT_PENDING}.</li>
 *   <li><b>예외 환불</b>({@link #refundByOperator}): 운영자({@code pointshop.refund})가 사유와 함께, 조건을 무시하고. 감사 {@code POINT_SHOP_REFUND}(돈 관련 5년).</li>
 *   <li><b>처리</b>: 아이템 회수(REVOKED) → 장착 중이었으면 자리 비움 → 고른 아바타였으면 프로필을 기본({@code basic-default})으로 → 재고 복원(지금 한정 재고인 품목만) →
 *       <b>사용 취소</b>({@link PointService#cancelUse} — 사유 {@code POINT_SHOP_REFUND}, 원래 묶음으로·만료 묶음은 30일 새 묶음) → 구매 REFUNDED.
 *       잠금 차례는 구매 → (신청) → 보유 줄 → 프로필 → 품목(재고) → 잔액(포인트가 마지막 — 1-1 규칙).</li>
 *   <li>이미 환불한 구매는 {@code 409 POINT_SHOP_ALREADY_REFUNDED}(구매 행 잠금으로 겹쳐도 한 번만).</li>
 * </ul>
 */
@Service
public class PointShopRefundService {

    private static final Logger log = LoggerFactory.getLogger(PointShopRefundService.class);

    public static final int REASON_MIN = 5;
    public static final int REASON_MAX = 200;
    /** 감사 대상 종류 */
    public static final String TARGET_PURCHASE = "POINT_SHOP_PURCHASE";

    private final PointShopItemStore items;
    private final PointShopPurchaseStore purchases;
    private final UserItemStore userItems;
    private final PointShopMemberStore members;
    private final PointService points;
    private final AuditLogService audit;
    private final NotificationPublisher operatorNotices;
    private final MemberNotificationPublisher memberNotices;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public PointShopRefundService(PointShopItemStore items, PointShopPurchaseStore purchases, UserItemStore userItems, PointShopMemberStore members,
                                  PointService points, AuditLogService audit, NotificationPublisher operatorNotices, MemberNotificationPublisher memberNotices,
                                  SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.items = items;
        this.purchases = purchases;
        this.userItems = userItems;
        this.members = members;
        this.points = points;
        this.audit = audit;
        this.operatorNotices = operatorNotices;
        this.memberNotices = memberNotices;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 회원 셀프 환불 — {@code POST /api/me/items/{id}/refund}. 정지 중이면 환불 신청을 만든다(202).
     *
     * @return 바로 환불({@code refunded} — 200) 또는 환불 신청({@code requested} — 202)
     * @throws UserItemNotFoundException          없거나 남의 보유 줄(404)
     * @throws PointShopAlreadyRefundedException  이미 환불함(409)
     * @throws PointShopRefundPendingException    환불 신청이 대기 중(409)
     * @throws PointShopRefundNotAllowedException 썼거나 기한이 지났다(409)
     * @throws PointShopRefundRejectedException   정지 중인데 이 구매의 신청이 거절된 적 있다(409)
     */
    @Transactional
    public SelfRefundOutcome refundSelf(long userId, long userItemId) {
        UserItemRow found = userItems.find(userItemId).filter(row -> row.userId() == userId).orElseThrow(UserItemNotFoundException::new);
        PurchaseRow purchase = purchases.findForUpdate(found.purchaseId()).orElseThrow(UserItemNotFoundException::new);
        UserItemRow item = userItems.findForUpdate(userItemId).orElseThrow(UserItemNotFoundException::new);
        if (purchase.status() == PointShopPurchaseStatus.REFUNDED || item.status() == UserItemStatus.REVOKED) {
            throw new PointShopAlreadyRefundedException();
        }
        if (purchases.hasRequest(purchase.id(), PointShopRefundRequestStatus.PENDING)) {
            throw new PointShopRefundPendingException();
        }
        Instant now = now();
        String blocked = PointShopRules.selfRefundBlock(item.status(), item.firstUsedAt(), purchase.createdAt(), items.refundDays(), now);
        if (blocked != null) {
            throw new PointShopRefundNotAllowedException(blocked);
        }
        boolean suspended = members.state(userId).filter(state -> !state.gone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED)).suspended();
        if (suspended) {
            return SelfRefundOutcome.requested(request(purchase, item, now));
        }
        long refunded = refund(purchase, item, PointShopRefundKind.SELF, null, null, now);
        return SelfRefundOutcome.refunded(new MyItemRefundView(refunded, members.balance(userId)));
    }

    /** 정지 중 셀프 환불 → 환불 신청(조건은 이미 판정했다 — 신청 시각으로 고정) + 운영자 알림 */
    private PointShopRefundRequestedView request(PurchaseRow purchase, UserItemRow item, Instant now) {
        if (purchases.hasRequest(purchase.id(), PointShopRefundRequestStatus.REJECTED)) {
            throw new PointShopRefundRejectedException();
        }
        long id = ids.nextId();
        try {
            purchases.insertRequest(id, purchase.id(), item.id(), purchase.userId(), now);
        } catch (DuplicateKeyException e) {
            throw new PointShopRefundPendingException(); // 같은 구매의 대기 신청이 동시에 — 부분 유일 색인이 막았다
        }
        operatorNotices.publish(AdminNotificationEvent.POINT_SHOP_REFUND_REQUESTED, NotificationContext.empty());
        log.info("포인트 상점 환불 신청 requestId={} purchaseId={} userId={}", id, purchase.id(), purchase.userId());
        return new PointShopRefundRequestedView(Long.toString(id), PointShopRefundRequestStatus.PENDING, now);
    }

    /**
     * 운영자가 환불 신청을 수락한다 — {@code POST /api/admin/point-shop/refund-requests/{id}/approve}. 조건을 다시 묻지 않는다(신청 순간에 판정·고정).
     *
     * @throws PointShopRefundRequestNotFoundException   없는 신청(404)
     * @throws PointShopRefundRequestNotPendingException 이미 수락·거절됨(409)
     * @throws PointShopAlreadyRefundedException         구매가 이미 환불됨(409)
     */
    @Transactional
    public PointShopRefundDecisionView approveRequest(long requestId, long operatorId, String ip) {
        RequestRow request = lockPending(requestId);
        PurchaseRow purchase = purchases.findForUpdate(request.purchaseId()).orElseThrow(PointShopRefundRequestNotFoundException::new);
        if (purchase.status() == PointShopPurchaseStatus.REFUNDED) {
            throw new PointShopAlreadyRefundedException();
        }
        UserItemRow item = userItems.byPurchaseForUpdate(purchase.id()).orElseThrow(PointShopRefundRequestNotFoundException::new);
        Instant now = now();
        if (!purchases.decide(requestId, PointShopRefundRequestStatus.APPROVED, operatorId, null, now)) {
            throw new PointShopRefundRequestNotPendingException();
        }
        long refunded = refund(purchase, item, PointShopRefundKind.REQUEST, operatorId, null, now);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.POINT_SHOP_REFUND, AuditResult.SUCCESS).target(TARGET_PURCHASE, Long.toString(purchase.id()))
                .with("kind", PointShopRefundKind.REQUEST.name()).with("requestId", Long.toString(requestId)).with("userId", Long.toString(purchase.userId()))
                .with("itemId", Long.toString(purchase.itemId())).with("itemCode", purchase.itemCode()).with("refundedPoints", refunded)
                .with("requestedAt", request.requestedAt().toString()));
        memberNotices.pointShopRefundDecided(purchase.userId(), requestId, purchase.id(), true, purchase.itemName(), refunded, null);
        return new PointShopRefundDecisionView(Long.toString(requestId), Long.toString(purchase.id()), PointShopRefundRequestStatus.APPROVED, refunded, now);
    }

    /**
     * 운영자가 환불 신청을 거절한다 — {@code POST /api/admin/point-shop/refund-requests/{id}/reject { reason }}. 아이템은 다시 쓸 수 있다.
     *
     * @throws ValidationException                       사유 길이({@code fields.reason})
     * @throws PointShopRefundRequestNotFoundException   없는 신청(404)
     * @throws PointShopRefundRequestNotPendingException 이미 수락·거절됨(409)
     */
    @Transactional
    public PointShopRefundDecisionView rejectRequest(long requestId, String reason, long operatorId, String ip) {
        String text = reasonOf(reason);
        RequestRow request = lockPending(requestId);
        PurchaseRow purchase = purchases.find(request.purchaseId()).orElseThrow(PointShopRefundRequestNotFoundException::new);
        Instant now = now();
        if (!purchases.decide(requestId, PointShopRefundRequestStatus.REJECTED, operatorId, text, now)) {
            throw new PointShopRefundRequestNotPendingException();
        }
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.POINT_SHOP_REFUND_REJECT, AuditResult.SUCCESS).target(TARGET_PURCHASE, Long.toString(purchase.id()))
                .with("requestId", Long.toString(requestId)).with("userId", Long.toString(purchase.userId())).with("itemId", Long.toString(purchase.itemId()))
                .with("itemCode", purchase.itemCode()).with("reason", text).with("requestedAt", request.requestedAt().toString()));
        memberNotices.pointShopRefundDecided(purchase.userId(), requestId, purchase.id(), false, purchase.itemName(), 0, text);
        return new PointShopRefundDecisionView(Long.toString(requestId), Long.toString(purchase.id()), PointShopRefundRequestStatus.REJECTED, 0, now);
    }

    /** 구매 행을 먼저 잠근 뒤(셀프 환불·신청과 같은 차례) 신청 행을 잠그고, 대기가 아니면 409 */
    private RequestRow lockPending(long requestId) {
        RequestRow found = purchases.findRequest(requestId).orElseThrow(PointShopRefundRequestNotFoundException::new);
        purchases.findForUpdate(found.purchaseId());
        RequestRow request = purchases.findRequestForUpdate(requestId).orElseThrow(PointShopRefundRequestNotFoundException::new);
        if (request.status() != PointShopRefundRequestStatus.PENDING) {
            throw new PointShopRefundRequestNotPendingException();
        }
        return request;
    }

    /**
     * 운영자 예외 환불 — {@code POST /api/admin/point-shop/purchases/{id}/refund { reason }}. 셀프 환불 조건을 보지 않는다(썼어도·기한이 지났어도).
     * 환불 신청이 대기 중인 구매는 신청을 수락·거절로 끝낸다({@code 409 POINT_SHOP_REFUND_PENDING}).
     *
     * @throws ValidationException                사유 길이({@code fields.reason})
     * @throws PointShopPurchaseNotFoundException 없는 구매(404)
     * @throws PointShopAlreadyRefundedException  이미 환불함(409)
     * @throws PointShopRefundPendingException    환불 신청 대기 중(409)
     */
    @Transactional
    public PointShopOperatorRefundView refundByOperator(long purchaseId, String reason, long operatorId, String ip) {
        String text = reasonOf(reason);
        PurchaseRow purchase = purchases.findForUpdate(purchaseId).orElseThrow(PointShopPurchaseNotFoundException::new);
        if (purchase.status() == PointShopPurchaseStatus.REFUNDED) {
            throw new PointShopAlreadyRefundedException();
        }
        if (purchases.hasRequest(purchaseId, PointShopRefundRequestStatus.PENDING)) {
            throw new PointShopRefundPendingException();
        }
        UserItemRow item = userItems.byPurchaseForUpdate(purchaseId).orElseThrow(PointShopPurchaseNotFoundException::new);
        Instant now = now();
        boolean selfRefundable = PointShopRules.selfRefundBlock(item.status(), item.firstUsedAt(), purchase.createdAt(), items.refundDays(), now) == null;
        long refunded = refund(purchase, item, PointShopRefundKind.EXCEPTION, operatorId, text, now);
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, AuditActions.POINT_SHOP_REFUND, AuditResult.SUCCESS).target(TARGET_PURCHASE, Long.toString(purchaseId))
                .with("kind", PointShopRefundKind.EXCEPTION.name()).with("userId", Long.toString(purchase.userId())).with("itemId", Long.toString(purchase.itemId()))
                .with("itemCode", purchase.itemCode()).with("refundedPoints", refunded).with("reason", text).with("selfRefundable", selfRefundable)
                .with("itemStatus", item.status().name());
        audit.record(item.firstUsedAt() == null ? event : event.with("firstUsedAt", item.firstUsedAt().toString()));
        return new PointShopOperatorRefundView(Long.toString(purchaseId), refunded);
    }

    private static String reasonOf(String reason) {
        String text = reason == null ? "" : reason.strip();
        if (text.length() < REASON_MIN || text.length() > REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("pointshop.refund.reason.length", REASON_MIN, REASON_MAX));
        }
        return text;
    }

    /** 회수 → 장착 해제 → (고른 아바타면) 기본 아바타 → 재고 복원 → 사용 취소(마지막 잠금) → 구매 REFUNDED. 돌려준 포인트 */
    private long refund(PurchaseRow purchase, UserItemRow item, PointShopRefundKind kind, Long operatorId, String reason, Instant now) {
        userItems.revoke(item.id(), now);
        userItems.unequipItem(item.id());
        ItemRow product = items.find(purchase.itemId()).orElseThrow();
        if (product.kind() == PointShopItemKind.PROFILE_AVATAR && product.avatarId() != null) {
            members.clearProfileImageIf(purchase.userId(), AvatarCatalog.profileImageOf(product.avatarId()), now);
        }
        if (product.stock() != null) {
            items.restoreStock(product.id());
        }
        long refundLedgerId = points.cancelUse(purchase.useLedgerId(), PointReason.POINT_SHOP_REFUND, PointService.REF_POINT_SHOP_PURCHASE, purchase.id());
        if (!purchases.markRefunded(purchase.id(), refundLedgerId, now, kind, operatorId, reason)) {
            throw new PointShopAlreadyRefundedException(); // 구매 행을 잠갔으니 오지 않는다 — 와도 트랜잭션째 되돌린다
        }
        log.info("포인트 상점 환불 purchaseId={} userId={} points={} kind={} operator={}", purchase.id(), purchase.userId(), purchase.price(), kind, operatorId);
        return purchase.price();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
