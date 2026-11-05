package com.community.emoticon.service;

import com.community.commerce.sales.BusinessInfo;
import com.community.commerce.sales.CommerceSalesStore;
import com.community.emoticon.dto.request.EmoticonPurchaseRequest;
import com.community.emoticon.dto.response.EmoticonPurchaseView;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.entity.EmoticonPurchaseStatus;
import com.community.emoticon.entity.EmoticonRefundKind;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonPackNotFoundException;
import com.community.emoticon.exception.EmoticonPurchaseBlockedException;
import com.community.emoticon.exception.EmoticonPurchaseNotFoundException;
import com.community.emoticon.exception.EmoticonRefundNotAllowedException;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore.AcquisitionRow;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.repository.master.EmoticonPurchaseStore.ListedRow;
import com.community.emoticon.repository.master.EmoticonPurchaseStore.PurchaseRow;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.emoticon.repository.master.EmoticonReadStore.MemberRef;
import com.community.emoticon.repository.master.EmoticonReadStore.MemberState;
import com.community.identity.service.IdentityVerificationService;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentOrderRequest;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.response.PaymentCheckoutView;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.payment.service.PaymentCancelService;
import com.community.payment.service.PaymentOrderService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로보티콘 유료 팩 구매(emoticon-contract S3) — 결제 준비 · 내 구매 내역 · 셀프 환불, 그리고 관리자 예외 환불이 같이 쓰는 환불 한 길({@link #refund}).
 *
 * <p><b>결제 준비</b>({@link #start}) — 한 트랜잭션. 차례: 로그인(탈퇴 401) → 정상 회원(정지 403 {@code ACCOUNT_RESTRICTED}) → 이메일 인증(403 {@code EMAIL_NOT_VERIFIED}) →
 * 본인인증·만 19세(403 {@code IDENTITY_REQUIRED}·{@code IDENTITY_UNDERAGE} — 계정에 한 번, commerce.md D3) → 환불 고지 동의(400 {@code fields.agreeRefundNotice}) →
 * 상점에 나온 팩(404) → 판매 중단(409 {@code EMOTICON_PACK_SUSPENDED}) → 무료 팩(409 {@code EMOTICON_PACK_FREE}) → 유료 판매 꺼짐(409 {@code EMOTICON_PAID_SALES_CLOSED}
 * — <b>새 구매만</b> 막는다, 이미 결제창에 들어간 주문의 완료는 처리기가 받는다) → 이미 가짐(409 {@code EMOTICON_PACK_OWNED} — 내가 만든 팩(최상위 {@code reason: CREATOR},
 * 후속 결정 1) · 산 팩 · 무료일 때 받은 팩(뺐어도)).
 * 같은 회원·팩의 준비는 권고 잠금으로 줄 세우고, 앞의 결제 대기는 <b>결제 행을 먼저</b> 닫은 뒤(결제 확정과 같은 잠금 차례) 구매를 CLOSED 로. 금액·이름·작가는 팩 행을 공유 잠금으로
 * 읽은 그때의 값(사본) — 유효 시간 30분(목업과 같다). 결제 ID·금액·유효 시각은 결제 모듈이 정한다.
 *
 * <p><b>셀프 환불</b>({@link #selfRefund}) — 본인의 결제 완료 구매만(없음·남의 것·결제 전 404) → 이미 환불 409 {@code EMOTICON_ALREADY_REFUNDED} → 썼다 409
 * {@code EMOTICON_REFUND_NOT_ALLOWED}({@code blockedBy USED}) → 기한 지남 같은 코드({@code EXPIRED}). 같은 요청 키의 다시 보내기는 입구(멱등 키)가 처음 응답을 준다.
 * 정지 회원도 환불은 된다(회원 돈을 묶어 두지 않는다 — commerce.md 12-3).
 */
@Service
public class EmoticonPurchaseService {

    private static final Logger log = LoggerFactory.getLogger(EmoticonPurchaseService.class);

    /** 결제 유효 시간(목업 30분 — 수량 선점이 없어 공구의 5분보다 길게 둔다) */
    public static final Duration CHECKOUT_VALID = Duration.ofMinutes(30);
    public static final String ORDER_TYPE = PaymentOrderType.ROBOTIKON_PACK.name();

    private final EmoticonPurchaseStore purchases;
    private final EmoticonPackStore packs;
    private final EmoticonAcquisitionStore acquisitions;
    private final EmoticonReadStore reads;
    private final EmoticonSettingsService settings;
    private final EmoticonContent content;
    private final IdentityVerificationService identity;
    private final PaymentOrderService payments;
    private final PaymentCancelService cancels;
    private final CommerceSalesStore commerce;
    private final EmoticonPurchaseNotices notices;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public EmoticonPurchaseService(EmoticonPurchaseStore purchases, EmoticonPackStore packs, EmoticonAcquisitionStore acquisitions, EmoticonReadStore reads,
                                   EmoticonSettingsService settings, EmoticonContent content, IdentityVerificationService identity, PaymentOrderService payments,
                                   PaymentCancelService cancels, CommerceSalesStore commerce, EmoticonPurchaseNotices notices, SnowflakeIdGenerator ids,
                                   ObjectProvider<Clock> clocks) {
        this.notices = notices;
        this.purchases = purchases;
        this.packs = packs;
        this.acquisitions = acquisitions;
        this.reads = reads;
        this.settings = settings;
        this.content = content;
        this.identity = identity;
        this.payments = payments;
        this.cancels = cancels;
        this.commerce = commerce;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 결제 준비 ───────────────────────────────────────────────────────────

    @Transactional
    public EmoticonPurchaseView.PurchaseStart start(long userId, String packIdText, EmoticonPurchaseRequest body) {
        MemberState member = reads.member(userId).filter(m -> !m.gone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (member.suspended()) {
            throw new EmoticonPurchaseBlockedException(EmoticonPurchaseBlockedException.ACCOUNT_RESTRICTED);
        }
        if (!member.emailVerified()) {
            throw new EmoticonPurchaseBlockedException(EmoticonPurchaseBlockedException.EMAIL_NOT_VERIFIED);
        }
        identity.requireAdult(userId);
        if (body == null || !Boolean.TRUE.equals(body.agreeRefundNotice())) {
            throw ValidationException.of("agreeRefundNotice", new MessageRef("emoticon.purchase.agreeRefundNotice"));
        }
        long packId = EmoticonShopService.parse(packIdText);
        PackRow seen = packId < 0 ? null : packs.find(packId).orElse(null);
        if (seen == null || !seen.status().published()) {
            throw new EmoticonPackNotFoundException();
        }
        purchases.lockMemberPack(userId, packId);
        // 앞의 결제 대기 — 결제 행을 먼저 닫는다(닫은 뒤 그 결제가 오면 늦은 결제로 자동 취소)
        List<Long> pending = purchases.pendingIds(userId, packId);
        for (long previous : pending) {
            payments.expireOpen(PaymentOrderType.ROBOTIKON_PACK, previous, "RETRY_PAYMENT");
        }
        PackRow p = packs.lockShare(packId).orElseThrow(EmoticonPackNotFoundException::new);
        if (!p.status().published()) {
            throw new EmoticonPackNotFoundException();
        }
        if (p.status() == EmoticonPackStatus.SUSPENDED) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_SUSPENDED, "error.EMOTICON_PACK_SUSPENDED.purchase");
        }
        if (p.price() == 0) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_FREE);
        }
        if (!settings.current().paidSalesEnabled()) {
            throw new EmoticonConflictException(EmoticonConflictException.PAID_SALES_CLOSED);
        }
        if (Long.valueOf(userId).equals(p.creatorId())) {
            throw EmoticonConflictException.creatorOwned("error.EMOTICON_PACK_OWNED.creator"); // 내가 만든 팩 — 사지 않아도 가진다(후속 결정 1)
        }
        Optional<AcquisitionRow> row = acquisitions.find(userId, packId);
        if (row.isPresent() && (row.get().owned() || !row.get().purchased())) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_OWNED, row.get().owned() ? "error.EMOTICON_PACK_OWNED" : "error.EMOTICON_PACK_OWNED.free");
        }
        Instant now = now();
        for (long previous : pending) {
            purchases.close(previous, now);
        }
        EmoticonContent.Loaded live = content.load(p.liveRevisionId());
        String name = live.revision().name();
        long purchaseId = ids.nextId();
        purchases.insert(new EmoticonPurchaseStore.NewPurchase(purchaseId, userId, packId, p.creatorId(), name, p.price(), now, now.plus(CHECKOUT_VALID), now));
        PaymentCheckout checkout = payments.create(new PaymentOrderRequest(userId, PaymentOrderType.ROBOTIKON_PACK, purchaseId, "로보티콘 「" + name + "」", p.price(),
                CHECKOUT_VALID));
        EmoticonPurchaseView.Customer customer = null;
        if (checkout.provider() == PaymentGatewayKind.PORTONE) {
            MemberRef ref = reads.members(List.of(userId)).get(userId);
            customer = new EmoticonPurchaseView.Customer(ref == null || ref.nickname() == null ? "" : ref.nickname(), "", purchases.email(userId));
        }
        log.info("로보티콘 팩 결제 준비 userId={} packId={} purchaseId={} amount={}", userId, packId, purchaseId, p.price());
        return new EmoticonPurchaseView.PurchaseStart(ORDER_TYPE, Long.toString(purchaseId), checkout.expiresAt(), PaymentCheckoutView.of(checkout), Long.toString(packId),
                customer);
    }

    // ── 내 구매 내역 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EmoticonPurchaseView.MyPurchaseList mine(long userId) {
        List<ListedRow> rows = purchases.mine(userId);
        if (rows.isEmpty()) {
            return new EmoticonPurchaseView.MyPurchaseList(List.of());
        }
        Map<Long, PackRow> packById = packs.byIds(rows.stream().map(r -> r.purchase().packId()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PackRow::id, x -> x));
        Map<Long, String> covers = covers(packById);
        Set<Long> creators = new HashSet<>();
        rows.forEach(r -> {
            if (r.purchase().creatorId() != null) {
                creators.add(r.purchase().creatorId());
            }
        });
        Map<Long, MemberRef> members = reads.members(creators);
        String seller = sellerName();
        Instant now = clock.instant();
        List<EmoticonPurchaseView.MyPurchase> out = new ArrayList<>();
        for (ListedRow r : rows) {
            PurchaseRow p = r.purchase();
            MemberRef creator = p.creatorId() == null ? null : members.get(p.creatorId());
            out.add(new EmoticonPurchaseView.MyPurchase(Long.toString(p.id()), Long.toString(p.packId()), p.packName(), covers.get(p.packId()), p.amount(), seller,
                    creator == null ? null : creator.nickname(), r.receiptUrl(), p.paidAt(), p.refundUntil(), p.usedAt(), p.status().name(), p.refundedAt(),
                    blockedBy(p, now) == null && p.status() == EmoticonPurchaseStatus.PAID));
        }
        return new EmoticonPurchaseView.MyPurchaseList(out);
    }

    // ── 셀프 환불 ───────────────────────────────────────────────────────────

    @Transactional
    public EmoticonPurchaseView.RefundResult selfRefund(long userId, String purchaseIdText) {
        if (reads.member(userId).filter(m -> !m.gone()).isEmpty()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        long id = EmoticonShopService.parse(purchaseIdText);
        PurchaseRow p = id < 0 ? null : purchases.lock(id).filter(x -> x.userId() == userId && x.paidAt() != null).orElse(null);
        if (p == null) {
            throw new EmoticonPurchaseNotFoundException(EmoticonPurchaseNotFoundException.PURCHASE);
        }
        if (p.status() == EmoticonPurchaseStatus.REFUNDED) {
            throw new EmoticonConflictException(EmoticonConflictException.ALREADY_REFUNDED);
        }
        String blocked = blockedBy(p, clock.instant());
        if (blocked != null) {
            throw new EmoticonRefundNotAllowedException(blocked);
        }
        PurchaseRow done = refund(p, EmoticonRefundKind.SELF, null, null);
        return new EmoticonPurchaseView.RefundResult(Long.toString(done.id()), done.status().name(), done.refundedAt(), done.amount());
    }

    /**
     * 환불 한 길(셀프·예외) — 잠근 결제 완료 구매를 REFUNDED 로, 보유를 빼고, 결제 모듈 취소 대기열에 전액 환불을 넣는다(요청 키 {@code EMOTICON_REFUND:<구매 ID>} — 같은 구매는 한 번).
     * 잠금 차례 = 구매 행 → 결제 행(취소 대기열). PG 취소는 커밋 뒤 그 자리에서 한 번 시도되고 실패하면 워커가 이어 받는다 — 완료 시각은 처리기 {@code onCanceled} 가 찍는다.
     * 환불 시각은 환불을 넣은 순간(보유를 뺀 때) — S4 정산이 환불한 달을 이 값으로 정한다.
     */
    PurchaseRow refund(PurchaseRow p, EmoticonRefundKind kind, String reason, Long operatorId) {
        Instant now = now();
        long cancelId = cancels.request(new PaymentCancelOrder(p.paymentOrderId(), p.amount(), CancelKind.REFUND,
                kind == EmoticonRefundKind.SELF ? "로보티콘 셀프 환불" : "로보티콘 예외 환불", "EMOTICON_REFUND:" + p.id(), operatorId));
        if (!purchases.markRefunded(p.id(), kind, reason, operatorId, cancelId, now)) {
            throw new EmoticonConflictException(EmoticonConflictException.ALREADY_REFUNDED);
        }
        acquisitions.revokePurchase(p.id(), now);
        notices.refunded(p, kind); // 셀프 환불 접수 · 운영자 예외 환불 알림 — 환불 트랜잭션 안(커밋돼야 보인다), PAID → REFUNDED 를 이긴 쪽만 여기 온다(후속 결정 2)
        log.info("로보티콘 팩 환불 purchaseId={} kind={} amount={} cancelId={}", p.id(), kind, p.amount(), cancelId);
        return purchases.find(p.id()).orElseThrow();
    }

    /** 셀프 환불을 막는 까닭 — 썼다 {@code USED} · 기한 지남 {@code EXPIRED} · 없으면 null(결제 완료 구매 기준) */
    static String blockedBy(PurchaseRow p, Instant now) {
        if (p.usedAt() != null) {
            return EmoticonRefundNotAllowedException.USED;
        }
        if (p.refundUntil() == null || !now.isBefore(p.refundUntil())) {
            return EmoticonRefundNotAllowedException.EXPIRED;
        }
        return null;
    }

    /** 팩 → 지금 상점 판의 대표 그림 */
    Map<Long, String> covers(Map<Long, PackRow> packById) {
        Map<Long, EmoticonContent.Loaded> lives = content.load(packById.values().stream().map(PackRow::liveRevisionId).filter(java.util.Objects::nonNull).toList());
        Map<Long, String> out = new java.util.HashMap<>();
        packById.values().forEach(p -> {
            EmoticonContent.Loaded live = p.liveRevisionId() == null ? null : lives.get(p.liveRevisionId());
            out.put(p.id(), live == null ? null : content.coverUrl(live));
        });
        return out;
    }

    /** 판매자 — 늘 사이트(카카오 방식). 커머스 사업자 정보의 상호(비었으면 null) */
    private String sellerName() {
        BusinessInfo info = commerce.businessInfo();
        return info == null || info.companyName() == null || info.companyName().isBlank() ? null : info.companyName().strip();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
