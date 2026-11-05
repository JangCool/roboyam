package com.community.emoticon.handler;

import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.entity.EmoticonPurchaseStatus;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore.AcquisitionRow;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.repository.master.EmoticonPurchaseStore.PurchaseRow;
import com.community.emoticon.service.EmoticonPurchaseNotices;
import com.community.emoticon.service.EmoticonSettingsService;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CanceledPayment;
import com.community.payment.dto.ManualCancelResolution;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.handler.PaymentOrderHandler;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 로보티콘 유료 팩 결제 처리기({@code ROBOTIKON_PACK} — emoticon-contract S3, commerce.md 8-6). 결제 확정·취소 완료 트랜잭션 안에서 결제 행을 쥔 채 불린다.
 *
 * <ul>
 *   <li><b>{@code onPaid}</b> — 도메인 사정으로 예외를 던지지 않는다(예외면 확정이 영원히 재시도된다). 잠금 차례 = 결제 행(결제 모듈) → 구매 행 → 팩 행 → 받기 줄.
 *       판정 차례(목업과 같다): 구매 없음·다른 회원·결제 대기 아님 → <b>금액</b>(구매 금액 ≠ 결제 금액) → <b>유효 시각</b>(포트원 {@code paidAt} > 구매 유효 시각) →
 *       <b>그사이 이미 가졌는지</b>(받기 줄이 지금 있음 · 무료일 때 받은 줄 — 뺐어도) · 판매 중 아님 · 그새 무료가 됨 → 모두 REJECT(결제 모듈이 전액 자동 취소, 구매는 CLOSED).
 *       받아들이면 구매 PAID(결제 시각 = 포트원 시각, 셀프 환불 기한 = 결제 시각 + <b>그때의</b> 환불 기간) + 보유(받기 줄 PURCHASE — 처음이면 새 줄과 받은 수 + 1, 환불했던 산 줄이면
 *       그 줄을 다시 — 받은 수는 사람마다 한 번). 산 팩에는 작가 받기 보상이 없다.</li>
 *   <li>결제 모듈이 먼저 거르는 것: 같은 결제의 두 번째 확정(조건부 UPDATE) · 결제 주문 유효 시각이 지난 결제(늦은 결제) · PG 금액·통화 불일치 — 처리기를 부르지 않는다.
 *       (결제 모듈의 차례는 늦은 결제 → 금액이라 둘 다 어긋나면 이상 표시가 {@code LATE_PAYMENT} 다 — 회원 결과는 같은 자동 취소)</li>
 *   <li><b>{@code onCanceled}</b> — 자동 취소(이상 결제)면 결제 대기 구매를 닫고, 환불 취소면 그 구매에 PG 취소 완료 시각만 찍는다(보유·상태는 환불을 넣을 때 이미 바꿨다).</li>
 *   <li><b>회원 알림</b>({@link EmoticonPurchaseNotices} — 후속 결정 2): 받아들임 = 구매 완료 · 자동 취소의 PG 취소 완료(또는 멈춘 자동 취소의 수동 처리) = 자동 취소.
 *       작가 자기 팩은 결제 준비가 막고, 여기서도 「이미 가짐」으로 거절한다(후속 결정 1).</li>
 * </ul>
 */
@Component
public class EmoticonPackPaymentHandler implements PaymentOrderHandler {

    private static final Logger log = LoggerFactory.getLogger(EmoticonPackPaymentHandler.class);

    public static final String REJECT_NOT_FOUND = "EMOTICON_PURCHASE_NOT_FOUND";
    public static final String REJECT_OTHER_MEMBER = "EMOTICON_PURCHASE_OTHER_MEMBER";
    public static final String REJECT_NOT_PENDING = "EMOTICON_PURCHASE_NOT_PENDING";
    public static final String REJECT_AMOUNT = "EMOTICON_PURCHASE_AMOUNT_MISMATCH";
    public static final String REJECT_LATE = "EMOTICON_PURCHASE_LATE";
    public static final String REJECT_OWNED = "EMOTICON_PACK_ALREADY_OWNED";
    public static final String REJECT_NOT_ON_SALE = "EMOTICON_PACK_NOT_ON_SALE";
    public static final String REJECT_NOW_FREE = "EMOTICON_PACK_NOW_FREE";

    private final EmoticonPurchaseStore purchases;
    private final EmoticonPackStore packs;
    private final EmoticonAcquisitionStore acquisitions;
    private final EmoticonSettingsService settings;
    private final EmoticonPurchaseNotices notices;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public EmoticonPackPaymentHandler(EmoticonPurchaseStore purchases, EmoticonPackStore packs, EmoticonAcquisitionStore acquisitions, EmoticonSettingsService settings,
                                      EmoticonPurchaseNotices notices, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.purchases = purchases;
        this.packs = packs;
        this.acquisitions = acquisitions;
        this.settings = settings;
        this.notices = notices;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public PaymentOrderType orderType() {
        return PaymentOrderType.ROBOTIKON_PACK;
    }

    @Override
    public PaymentDecision onPaid(PaidPayment payment) {
        Optional<PurchaseRow> found = purchases.lock(payment.orderRefId());
        if (found.isEmpty()) {
            return PaymentDecision.reject(REJECT_NOT_FOUND);
        }
        PurchaseRow p = found.get();
        if (p.userId() != payment.userId()) {
            return PaymentDecision.reject(REJECT_OTHER_MEMBER);
        }
        if (p.status() != EmoticonPurchaseStatus.PENDING) {
            return PaymentDecision.reject(REJECT_NOT_PENDING);
        }
        String reject = judge(p, payment);
        Instant now = now();
        if (reject != null) {
            purchases.close(p.id(), now);
            log.info("로보티콘 팩 결제 거절 purchaseId={} reason={}", p.id(), reject);
            return PaymentDecision.reject(reject);
        }
        Instant paidAt = payment.paidAt().truncatedTo(ChronoUnit.MICROS);
        Instant refundUntil = paidAt.plus(Duration.ofDays(settings.current().refundDays()));
        if (!purchases.markPaid(p.id(), payment.paymentOrderId(), paidAt, refundUntil)) {
            return PaymentDecision.reject(REJECT_NOT_PENDING);
        }
        Optional<AcquisitionRow> row = acquisitions.find(p.userId(), p.packId());
        if (row.isPresent()) {
            acquisitions.repurchase(row.get().id(), p.id(), now); // 환불했던 산 줄 — 받은 수는 사람마다 한 번이라 그대로
        } else {
            acquisitions.insertPurchase(ids.nextId(), p.userId(), p.packId(), p.id(), now);
            packs.incrementAcquire(p.packId());
        }
        notices.paid(p); // 구매 완료 알림 — 확정 트랜잭션 안(커밋돼야 보인다), PENDING → PAID 를 이긴 쪽만 여기 온다
        log.info("로보티콘 팩 결제 완료 purchaseId={} packId={} amount={}", p.id(), p.packId(), p.amount());
        return PaymentDecision.accept();
    }

    /** 금액 → 유효 시각 → 그사이 이미 가졌는지 · 판매 중 · 무료가 됨 — 거절 사유(받아들이면 null). 팩 행은 여기서 잠근다(무료 받기·가격 바꾸기와 줄을 선다) */
    private String judge(PurchaseRow p, PaidPayment payment) {
        if (payment.amount() != p.amount()) {
            return REJECT_AMOUNT;
        }
        if (payment.paidAt().isAfter(p.expiresAt())) {
            return REJECT_LATE;
        }
        PackRow pack = packs.lock(p.packId()).orElse(null);
        if (pack != null && Long.valueOf(p.userId()).equals(pack.creatorId())) {
            return REJECT_OWNED; // 내가 만든 팩 — 사지 않아도 가진다(후속 결정 1 · 결제 준비가 먼저 막는다, 그사이 팩 주인이 바뀐 경우의 그물)
        }
        Optional<AcquisitionRow> row = acquisitions.find(p.userId(), p.packId());
        if (row.isPresent() && (row.get().owned() || !row.get().purchased())) {
            return REJECT_OWNED; // 그사이 무료로 받았거나 다른 결제로 샀다 · 무료일 때 받은 회원(뺐어도 다시 넣을 수 있다)
        }
        if (pack == null || pack.status() != EmoticonPackStatus.APPROVED) {
            return REJECT_NOT_ON_SALE;
        }
        if (pack.price() == 0) {
            return REJECT_NOW_FREE; // 결제창에 있는 동안 무료가 됐다 — 돈을 받지 않는다
        }
        return null;
    }

    @Override
    public void onCanceled(CanceledPayment cancellation) {
        Instant now = now();
        if (cancellation.anomaly() != null) {
            purchases.close(cancellation.orderRefId(), now); // 이상 결제의 자동 취소 — 결제 대기였으면 닫는다(이미 닫혔으면 그대로)
            // 자동 취소 알림 — 취소 한 건마다 한 번(결제 모듈의 SUCCEEDED 조건부), 늦은 결제·금액 불일치·도메인 거절 모두 이 한 곳(후속 결정 2)
            purchases.find(cancellation.orderRefId()).filter(p -> p.userId() == cancellation.userId()).ifPresent(p -> notices.autoCanceled(p, cancellation.amount()));
            return;
        }
        if (cancellation.kind() == CancelKind.REFUND) {
            purchases.completeRefund(cancellation.cancelId(), now);
        }
    }

    /** 멈춘 환불 취소를 운영자가 직접 처리했다(콘솔 취소·계좌 송금·표시만) — 환불 완료 시각 */
    @Override
    public void onCancelResolvedManually(ManualCancelResolution resolution) {
        if (resolution.kind() == CancelKind.REFUND) {
            purchases.completeRefund(resolution.cancelId(), resolution.resolvedAt() == null ? now() : resolution.resolvedAt().truncatedTo(ChronoUnit.MICROS));
        }
        if (resolution.kind() == CancelKind.AUTO_REJECT) {
            // 멈췄던 자동 취소를 운영자가 처리했다 — 그때 자동 취소 알림을 한 번(GAVE_UP 자체는 회원에게 알리지 않는다, 후속 결정 2)
            purchases.find(resolution.orderRefId()).filter(p -> p.userId() == resolution.userId()).ifPresent(p -> notices.autoCanceled(p, resolution.amount()));
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
