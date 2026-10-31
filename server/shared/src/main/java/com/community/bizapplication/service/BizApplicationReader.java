package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPhase;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.repository.master.BizApplicationStore.DepositRow;
import com.community.bizapplication.repository.master.BizApplicationStore.EventRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizContractStore;
import com.community.bizapplication.repository.master.BizContractStore.ContractRow;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청 건 읽기 — 화면(공개·관리자)이 그릴 재료를 한 번에 모은다({@link Snapshot}·{@link Summary}). 큐·묶음·보이는 상태는 시각으로 계산한다({@link #queue}·{@link #bucket}).
 * 목록은 줄마다 조회하지 않고 ID 를 모아 한 번에 읽는다.
 */
@Service
@Transactional(readOnly = true)
public class BizApplicationReader {

    /** 관리자 큐(탭) — admin-web {@code BizApplicationQueue} */
    public enum Queue { REVIEW, PAYMENT, DEPOSIT, DEPOSIT_OVERDUE, LIVE, REFUND, CLOSED }

    /** 공개 목록 묶음 — user-web {@code BizApplicationBucket}(ALL 제외) */
    public enum Bucket { REVIEW, AWAITING_PAYMENT, AWAITING_DEPOSIT, UPCOMING, RUNNING, ENDED, CLOSED }

    /** 목록 한 줄의 재료 */
    public record Summary(AppRow app, List<ItemRow> items, RefundRow refund) {
    }

    /** 상세의 재료 */
    public record Snapshot(AppRow app, List<ItemRow> items, CreativeRow creative, List<EventRow> events, List<ContractRow> contracts, DepositRow deposit,
                           RefundRow refund, String approvedByName, OrderRow payment, String applicantName) {
    }

    private final BizApplicationStore store;
    private final BizContractStore contracts;
    private final BizRefundStore refunds;
    private final PaymentOrderStore paymentOrders;
    private final Clock clock;

    public BizApplicationReader(BizApplicationStore store, BizContractStore contracts, BizRefundStore refunds, PaymentOrderStore paymentOrders,
                                ObjectProvider<Clock> clocks) {
        this.store = store;
        this.contracts = contracts;
        this.refunds = refunds;
        this.paymentOrders = paymentOrders;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    public Instant now() {
        return clock.instant();
    }

    public Snapshot snapshot(long id) {
        return snapshotOf(store.find(id).orElseThrow(BizApplicationException::notFound));
    }

    /** 그 회원의 건 — 남의 건은 404 */
    public Snapshot snapshotOwned(long id, long userId) {
        return snapshotOf(store.findOwned(id, userId).orElseThrow(BizApplicationException::notFound));
    }

    public Snapshot snapshotOf(AppRow app) {
        return new Snapshot(app, store.items(app.id()), store.latestCreative(app.id()).orElse(null), store.events(app.id()), contracts.list(app.id()),
                store.deposit(app.id()).orElse(null), refunds.latest(app.id()).orElse(null), store.operatorName(app.approvedBy()),
                app.paymentOrderId() == null ? null : paymentOrders.findById(app.paymentOrderId()).orElse(null), store.nickname(app.userId()));
    }

    /** 목록의 재료 — 줄·환불을 ID 로 모아 한 번에 */
    public List<Summary> summaries(List<AppRow> apps) {
        List<Long> ids = apps.stream().map(AppRow::id).toList();
        Map<Long, List<ItemRow>> items = store.items(ids);
        Map<Long, RefundRow> latest = refunds.latest(ids);
        return apps.stream().map(a -> new Summary(a, items.getOrDefault(a.id(), List.of()), latest.get(a.id()))).toList();
    }

    public Map<Long, CreativeRow> creatives(List<Long> ids) {
        return store.latestCreatives(ids);
    }

    /** 관리자 큐의 재료 — 그 도메인의 건만 */
    public List<AppRow> forAdmin(com.community.bizapplication.entity.BizDomain domain, com.community.bizapplication.entity.BizApplicationKind kind, String q) {
        return store.forAdmin(domain, kind, q);
    }

    /** 운영자 이름(없으면 null) */
    public String operatorName(Long operatorId) {
        return store.operatorName(operatorId);
    }

    /** 소재 버전 전부(오래된 것 먼저) */
    public List<CreativeRow> creativeVersions(long id) {
        return store.creatives(id);
    }

    /** 건 하나(없으면 404) — 처리 전에 도메인 권한을 가를 때 */
    public AppRow app(long id) {
        return store.find(id).orElseThrow(BizApplicationException::notFound);
    }

    public List<AppRow> byUser(long userId) {
        return store.byUser(userId);
    }

    // ── 계산 ────────────────────────────────────────────────────────────────

    /** 자리를 차지하는 상품(한도가 있는 줄) — 핫딜 게시만이면 null */
    public static BizProduct capacityProduct(List<ItemRow> items) {
        return items.stream().map(ItemRow::product).filter(p -> p != BizProduct.HOTDEAL_POST).findFirst().orElse(null);
    }

    /** 예약의 시각 단계 — 예약이 아니면 null */
    public static BizPhase phase(AppRow app, Instant now) {
        return app.status() == BizApplicationStatus.SCHEDULED ? BizPhase.of(app.startAt(), app.endAt(), now) : null;
    }

    /** 관리자 화면의 보이는 상태 — 예약은 시각으로 SCHEDULED·RUNNING·ENDED */
    public static String adminStatus(AppRow app, Instant now) {
        BizPhase p = phase(app, now);
        if (p == null) {
            return app.status().name();
        }
        return switch (p) {
            case UPCOMING -> "SCHEDULED";
            case RUNNING -> "RUNNING";
            case ENDED -> "ENDED";
        };
    }

    public static boolean refundOpen(RefundRow refund) {
        return refund != null && refund.status() == BizRefundStatus.REQUESTED;
    }

    /** 관리자 큐 — 환불 심사 대기가 먼저, 입금 대기는 기한이 지났으면 「입금 기한 지남」 */
    public static Queue queue(AppRow app, RefundRow refund, Instant now) {
        if (refundOpen(refund)) {
            return Queue.REFUND;
        }
        return switch (app.status()) {
            case SUBMITTED -> Queue.REVIEW;
            case AWAITING_PAYMENT -> Queue.PAYMENT;
            case AWAITING_DEPOSIT -> app.depositDeadlineAt() != null && app.depositDeadlineAt().isBefore(now) ? Queue.DEPOSIT_OVERDUE : Queue.DEPOSIT;
            case SCHEDULED -> phase(app, now) == BizPhase.ENDED ? Queue.CLOSED : Queue.LIVE;
            case TAKEN_DOWN -> app.endAt().isAfter(now) ? Queue.LIVE : Queue.CLOSED;
            default -> Queue.CLOSED;
        };
    }

    /** 공개 묶음(user-web {@code UtilBusiness.bucketOf} 와 같다 — 내림은 진행 중 묶음) */
    public static Bucket bucket(AppRow app, Instant now) {
        return switch (app.status()) {
            case SUBMITTED -> Bucket.REVIEW;
            case AWAITING_PAYMENT -> Bucket.AWAITING_PAYMENT;
            case AWAITING_DEPOSIT -> Bucket.AWAITING_DEPOSIT;
            case SCHEDULED -> switch (phase(app, now)) {
                case UPCOMING -> Bucket.UPCOMING;
                case RUNNING -> Bucket.RUNNING;
                case ENDED -> Bucket.ENDED;
            };
            case TAKEN_DOWN -> Bucket.RUNNING;
            default -> Bucket.CLOSED;
        };
    }
}
