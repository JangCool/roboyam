package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.entity.RejectReason;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.Resolution;
import com.community.groupbuy.entity.ReturnDisposition;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.BankTransferStore;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.GroupBuyRequestStore;
import com.community.groupbuy.repository.master.GroupBuyRequestStore.RequestRow;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentOrderRequest;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.service.PaymentOrderService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 취소·반품 요청(commerce.md 10-3·10-8, 계약 결정 15·44·54·55·68·69·72·73·83). 요청은 주문 상태가 아니라 따로 다룬다(철회·거절이 주문 상태를 흔들지 않게).
 * 잠금 차례: <b>주문 행 → 요청 행 → 수량 → 결제 행</b>(결제가 PAID 인 흐름 — 결정 37). 반품비 부족분 결제를 닫는 흐름만 결제 행 먼저(READY — 결정 37).
 *
 * <h3>회원</h3>
 * <ul>
 *   <li><b>발송 전 취소 요청</b>(CANCEL): CONFIRMED·PREPARING 주문. 운영자가 승인하면 그 줄·수량을 취소하고 돌려준다(일부면 배송비 재계산 — 결정 42). 거절은 「이미 발송함」 + 송장 입력만(10-3).</li>
 *   <li><b>반품</b>(RETURN): 배송완료 주문, 줄마다 사유·수량(결정 44), 회수 방식, 하자·오배송·훼손이면 「교환」도(결정 54), 사진 최대 3장.
 *       기한: 단순 변심·기타 줄은 배송완료 7일, 업체 부담 줄은 3개월. 3개월이 지나도 업체 부담 줄만이면 회원이 적은 「하자를 안 날」(배송완료 날 ~ 오늘, 한국 날짜)부터
 *       30일 안이면 받는다(제17조 ③ — 사용자 지시 보강 2026-10-28). 운영자는 그 날짜를 보고 거절 사유 코드로 거절할 수 있다(감사에 날짜).
 *       미리 보기는 회수 방식마다 결과를 보이고(음수 가능 — 결정 68), 음수인 방식을 고르면 부족분을 추가 결제({@code GROUP_BUY_RETURN_FEE}, 30분)해야 접수(그 전엔 AWAITING_FEE, 하루 뒤 닫힘).</li>
 *   <li>본 상품을 모두 빼며 추가 구성상품만 남기면 409 {@code GROUP_BUY_ADDON_ONLY}(결정 88). 주문당 열린 요청 하나(409 {@code GROUP_BUY_REQUEST_OPEN}).</li>
 *   <li><b>철회</b>: 회수 확인 전까지. 낸 부족분은 돌려준다.</li>
 * </ul>
 * <h3>운영자</h3>
 * <ul>
 *   <li>승인(반품은 회수 안내) · 회수 송장 · 회수 확인(돌려받은 날 — 3영업일 기한 시작) · 완료(줄마다 처리 방법과 수량 — 합 = 줄 수량, 다시 판매는 모집 중 + 단순 변심만 — 결정 15,
 *       고객발송 하자면 보상액 — 상한은 공구 설정, 남은 결제액을 넘는 몫은 계좌 송금 — 결정 6·39).</li>
 *   <li>승인·완료 때 다시 계산한 값이 미리 보기와 다르면 409 {@code GROUP_BUY_REFUND_AMOUNT_CHANGED}(두 값) — 화면이 확인 뒤 {@code expectedAmount} 에 새 값을 실어 다시(결정 72).
 *       그 결제에 열린 대사 이슈(콘솔 직접 취소)가 있으면 409 {@code GROUP_BUY_PAYMENT_UNRECONCILED}(결정 73). 서버가 계산한 환불은 금액과 관계없이 일반 권한, 보상액은 고액 문턱 누계에 든다(결정 70).</li>
 *   <li>거절은 사유 코드 + 근거 사진 1~3장(결정 55). 가치 감소 사유는 상품에 청약철회 제한 안내가 있고 단순 변심 요청일 때만.</li>
 *   <li>교환: 회수 확인 뒤 같은 주문에 재발송(환불 0·판매 수량 그대로·교환 출고 기록·에스크로 다시 등록). 교환할 물건이 없으면 환불로 전환(회원 알림).</li>
 * </ul>
 */
@Service
public class GroupBuyRequestService {

    /** 단순 변심 반품 기한(배송완료부터 — 제17조 ①) */
    static final int CHANGE_OF_MIND_DAYS = 7;
    /** 업체 부담 반품 기한(배송완료부터 3개월 — L2) */
    static final int SELLER_FAULT_MONTHS = 3;
    /** 업체 부담 반품 — 그 사실을 안 날부터(제17조 ③) */
    static final int SELLER_FAULT_KNOWN_DAYS = 30;
    /** 반품비 부족분 결제 유효 시간·요청이 닫히는 때(결정 68) */
    static final Duration FEE_PAYMENT_VALID = Duration.ofMinutes(30);
    static final Duration FEE_REQUEST_LIFETIME = Duration.ofDays(1);
    static final int PHOTO_MAX = 3;

    /** 요청 줄 입력 — 단품·수량·사유(발송 전 취소는 사유 없음) */
    public record LineInput(long skuId, int qty, RequestReason reason) {
    }

    /** 요청 입력 — {@code defectKnownOn} 은 업체 부담 반품의 「하자를 안 날」(배송완료 + 3개월이 지났을 때 필요, 그 전엔 적지 않아도 된다) */
    public record RequestInput(RequestType type, CollectionMethod method, Resolution resolution, List<LineInput> lines, String memo, List<Long> photoIds,
                               Long expectedAmount, LocalDate defectKnownOn) {

        public RequestInput(RequestType type, CollectionMethod method, Resolution resolution, List<LineInput> lines, String memo, List<Long> photoIds,
                            Long expectedAmount) {
            this(type, method, resolution, lines, memo, photoIds, expectedAmount, null);
        }
    }

    /** 미리 보기 — 발송 전 취소는 {@code cancel}, 반품은 회수 방식마다 */
    public record Preview(GroupBuyRefundCalculator.Result cancel, Map<CollectionMethod, GroupBuyRefundCalculator.Result> byMethod, boolean sellerFault) {
    }

    /** 만든 요청 — 부족분 결제가 필요하면 결제창 정보 */
    public record Created(long requestId, RequestStatus status, long previewAmount, PaymentCheckout feePayment) {
    }

    /** 처리 방법 입력 — 단품·방법·수량 */
    public record DispositionInput(long skuId, ReturnDisposition disposition, int qty) {
    }

    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final GroupBuyRequestStore requests;
    private final GroupBuyRefundStore refundStore;
    private final GroupBuyRefundService refunds;
    private final ParticipationCancelService cancels;
    private final GroupBuyQtyService qty;
    private final FulfillmentService fulfillment;
    private final GroupBuyEscrowWorker escrow;
    private final PaymentOrderService paymentOrders;
    private final BankTransferStore transfers;
    private final GroupBuyAudit audit;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GroupBuyRequestService(ParticipationStore participations, GroupBuyStore groups, GroupBuyRequestStore requests, GroupBuyRefundStore refundStore,
                                  GroupBuyRefundService refunds, ParticipationCancelService cancels, GroupBuyQtyService qty, FulfillmentService fulfillment,
                                  GroupBuyEscrowWorker escrow, PaymentOrderService paymentOrders, BankTransferStore transfers, GroupBuyAudit audit, SnowflakeIdGenerator ids,
                                  ApplicationEventPublisher events, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.participations = participations;
        this.groups = groups;
        this.requests = requests;
        this.refundStore = refundStore;
        this.refunds = refunds;
        this.cancels = cancels;
        this.qty = qty;
        this.fulfillment = fulfillment;
        this.escrow = escrow;
        this.paymentOrders = paymentOrders;
        this.transfers = transfers;
        this.audit = audit;
        this.ids = ids;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    /** 미리 보기(요청 화면 — 승인 화면과 같은 계산기). 남의 주문은 404 */
    public Preview preview(long userId, long participationId, RequestType type, List<LineInput> lines) {
        ParticipationRow p = participations.find(participationId).filter(r -> r.userId() == userId).orElseThrow(GroupBuyException::orderNotFound);
        GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow(GroupBuyException::notFound);
        List<ItemRow> items = participations.items(p.id());
        List<GroupBuyRefundCalculator.Line> calc = toLines(items, type, lines);
        checkAddonOnly(items, calc);
        if (type == RequestType.CANCEL) {
            return new Preview(GroupBuyRefundCalculator.cancel(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(g), held(p, items), calc, true), Map.of(),
                    false);
        }
        Map<CollectionMethod, GroupBuyRefundCalculator.Result> byMethod = new EnumMap<>(CollectionMethod.class);
        for (CollectionMethod m : CollectionMethod.values()) {
            byMethod.put(m, GroupBuyRefundCalculator.returnRefund(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(g), held(p, items), calc, m));
        }
        return new Preview(null, byMethod, byMethod.get(CollectionMethod.COMPANY_PICKUP).sellerFault());
    }

    /**
     * 요청을 만든다.
     *
     * @throws GroupBuyException 주문 없음 · 상태가 아님({@code ORDER_STATE}) · 기한 지남 · 열린 요청 · 추가상품만 남음 · 교환 불가 · 화면 금액이 다름({@code AMOUNT_CHANGED})
     */
    public Created create(long userId, long participationId, RequestInput in) {
        if (in == null || in.type() == null || (in.type() == RequestType.RETURN && in.method() == null)) {
            throw ValidationException.of("type", new MessageRef("groupbuy.request.lines.invalid"));
        }
        if (in.memo() != null && in.memo().strip().length() > 1000) {
            throw ValidationException.of("memo", new MessageRef("groupbuy.request.memo.invalid"));
        }
        List<Long> photos = in.photoIds() == null ? List.of() : List.copyOf(new HashSet<>(in.photoIds()));
        if (photos.size() > PHOTO_MAX) {
            throw ValidationException.of("photoIds", new MessageRef("groupbuy.request.photos.invalid"));
        }
        Created created;
        try {
            created = tx.execute(status -> {
                Instant now = now();
                ParticipationRow p = participations.lock(participationId).filter(r -> r.userId() == userId).orElseThrow(GroupBuyException::orderNotFound);
                GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow(GroupBuyException::notFound);
                List<ItemRow> items = participations.items(p.id());
                List<GroupBuyRefundCalculator.Line> calc = toLines(items, in.type(), in.lines());
                checkAddonOnly(items, calc);
                if (requests.hasOpen(p.id())) {
                    throw GroupBuyException.requestOpen();
                }
                boolean sellerFault = calc.stream().anyMatch(l -> l.reason() != null && l.reason().sellerFault());
                Resolution resolution = in.resolution() == null ? Resolution.REFUND : in.resolution();
                long amount;
                LocalDate knownOn = null;
                if (in.type() == RequestType.CANCEL) {
                    if (!EnumSet.of(ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING).contains(p.status())) {
                        throw GroupBuyException.orderState();
                    }
                    if (resolution != Resolution.REFUND) {
                        throw GroupBuyException.exchangeNotAllowed();
                    }
                    amount = GroupBuyRefundCalculator.cancel(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(g), held(p, items), calc, true).total();
                } else {
                    if (p.status() != ParticipationStatus.DELIVERED) {
                        throw GroupBuyException.orderState();
                    }
                    knownOn = checkReturnWindow(p, calc, in.defectKnownOn(), now);
                    if (resolution == Resolution.EXCHANGE && (!sellerFault || calc.stream().anyMatch(l -> !l.reason().sellerFault()))) {
                        throw GroupBuyException.exchangeNotAllowed(); // 교환은 하자·오배송·훼손 줄만(결정 54)
                    }
                    amount = resolution == Resolution.EXCHANGE ? 0
                            : GroupBuyRefundCalculator.returnRefund(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(g), held(p, items), calc, in.method()).total();
                }
                if (in.expectedAmount() != null && in.expectedAmount() != amount) {
                    throw GroupBuyException.amountChanged(amount);
                }
                long fee = amount < 0 ? -amount : 0;
                RequestStatus initial = fee > 0 ? RequestStatus.AWAITING_FEE : RequestStatus.REQUESTED;
                long requestId = ids.nextId();
                requests.insert(new GroupBuyRequestStore.NewRequest(requestId, p.id(), g.id(), userId, in.type(), initial, in.method(), resolution, sellerFault,
                        in.memo() == null || in.memo().isBlank() ? null : in.memo().strip(), amount, fee, fee > 0 ? now.plus(FEE_REQUEST_LIFETIME) : null, knownOn, now));
                for (GroupBuyRefundCalculator.Line l : calc) {
                    ItemRow item = items.stream().filter(i -> i.id() == l.itemId()).findFirst().orElseThrow();
                    requests.insertItem(ids.nextId(), requestId, item.id(), item.skuId(), l.qty(), l.reason() == null ? RequestReason.OTHER : l.reason());
                }
                if (!photos.isEmpty() && requests.attachPhotos(requestId, "MEMBER", userId, photos, now) != photos.size()) {
                    throw ValidationException.of("photoIds", new MessageRef("groupbuy.request.photos.invalid"));
                }
                PaymentCheckout checkout = null;
                if (fee > 0) {
                    checkout = paymentOrders.create(new PaymentOrderRequest(userId, PaymentOrderType.GROUP_BUY_RETURN_FEE, requestId, "반품 배송비 " + p.orderNo(), fee,
                            FEE_PAYMENT_VALID));
                    requests.attachFeePayment(requestId, checkout.paymentOrderId(), now);
                }
                events.publishEvent(new GroupBuyEvents.RequestChanged(requestId, p.id(), userId, initial.name(), in.type().name()));
                return new Created(requestId, initial, amount, checkout);
            });
        } catch (DuplicateKeyException e) {
            throw GroupBuyException.requestOpen(); // 부분 유일 색인 — 동시에 두 번(결정 66)
        }
        return created;
    }

    /** 부족분 결제 다시 열기 — 요청이 닫히기 전이면 유효한 결제창을 그대로, 없으면 새로(30분) */
    public PaymentCheckout reopenFeePayment(long userId, long requestId) {
        return tx.execute(status -> {
            RequestRow r = requests.lock(requestId).filter(row -> row.userId() == userId).orElseThrow(GroupBuyException::requestNotFound);
            if (r.status() != RequestStatus.AWAITING_FEE || !r.closesAt().isAfter(now())) {
                throw GroupBuyException.requestNotOpen();
            }
            Optional<PaymentCheckout> open = paymentOrders.openCheckout(PaymentOrderType.GROUP_BUY_RETURN_FEE, requestId, Duration.ofMinutes(1));
            if (open.isPresent()) {
                return open.get();
            }
            ParticipationRow p = participations.find(r.participationId()).orElseThrow();
            PaymentCheckout checkout = paymentOrders.create(new PaymentOrderRequest(userId, PaymentOrderType.GROUP_BUY_RETURN_FEE, requestId, "반품 배송비 " + p.orderNo(),
                    r.feeAmount(), FEE_PAYMENT_VALID));
            requests.attachFeePayment(requestId, checkout.paymentOrderId(), now());
            return checkout;
        });
    }

    /** 철회 — 회수 확인 전까지(AWAITING_FEE·REQUESTED·APPROVED). 낸 부족분은 돌려준다 */
    public void withdraw(long userId, long requestId) {
        RequestRow before = requests.find(requestId).filter(row -> row.userId() == userId).orElseThrow(GroupBuyException::requestNotFound);
        closeFeePaymentFirst(before);
        tx.executeWithoutResult(status -> {
            Instant now = now();
            ParticipationRow p = participations.lock(before.participationId()).orElseThrow();
            RequestRow r = requests.lock(requestId).orElseThrow();
            if (!requests.transition(r.id(), EnumSet.of(RequestStatus.AWAITING_FEE, RequestStatus.REQUESTED, RequestStatus.APPROVED), RequestStatus.WITHDRAWN, null, now)) {
                throw GroupBuyException.requestNotOpen();
            }
            refundFeeHeld(p, r, null, "WITHDRAWN", now);
            events.publishEvent(new GroupBuyEvents.RequestChanged(r.id(), p.id(), r.userId(), RequestStatus.WITHDRAWN.name(), null));
        });
    }

    /** 부족분을 결제하지 않은 요청을 닫는다(하루 — 결정 68). 닫은 수 */
    public int closeUnpaid() {
        int n = 0;
        for (long id : requests.awaitingFeeDue(now(), 100)) {
            RequestRow r = requests.find(id).orElseThrow();
            closeFeePaymentFirst(r);
            Boolean done = tx.execute(status -> {
                ParticipationRow p = participations.lock(r.participationId()).orElseThrow();
                RequestRow locked = requests.lock(id).orElseThrow();
                if (!requests.transition(id, EnumSet.of(RequestStatus.AWAITING_FEE), RequestStatus.EXPIRED, null, now())) {
                    return false;
                }
                refundFeeHeld(p, locked, null, "EXPIRED", now());
                return true;
            });
            if (Boolean.TRUE.equals(done)) {
                n++;
            }
        }
        return n;
    }

    /** 결제 중(READY) 부족분 결제를 결제 행 먼저 닫는다(결정 37 — 닫은 뒤 도착한 결제는 늦은 결제로 자동 취소) */
    private void closeFeePaymentFirst(RequestRow r) {
        if (r.status() == RequestStatus.AWAITING_FEE) {
            tx.executeWithoutResult(status -> paymentOrders.expireOpen(PaymentOrderType.GROUP_BUY_RETURN_FEE, r.id(), "REQUEST_CLOSED"));
        }
    }

    // ── 사진 ────────────────────────────────────────────────────────────────

    /** 회원 사진 올리기(요청 없이 먼저 — 붙지 않으면 24시간 뒤 정리). 저장은 미디어 저장소(2b-4 컨트롤러), 여기는 키만 */
    public long registerMemberPhoto(long userId, String imageKey) {
        return registerPhoto("MEMBER", userId, imageKey);
    }

    /** 운영자 근거 사진(반품 거절 — 결정 55) */
    public long registerOperatorPhoto(long operatorId, String imageKey) {
        return registerPhoto("OPERATOR", operatorId, imageKey);
    }

    private long registerPhoto(String kind, long ownerId, String imageKey) {
        if (imageKey == null || imageKey.isBlank() || imageKey.length() > 300) {
            throw ValidationException.of("imageKey", new MessageRef("groupbuy.request.photos.invalid"));
        }
        long id = ids.nextId();
        tx.executeWithoutResult(status -> requests.insertPhoto(id, kind, ownerId, imageKey.strip(), now()));
        return id;
    }

    /** 붙지 않은 지 24시간 지난 사진 정리 — 지운 이미지 키(저장소 파일 정리는 부르는 쪽) */
    public List<String> cleanupLoosePhotos() {
        return tx.execute(status -> requests.deleteLoosePhotos(now().minus(Duration.ofHours(24))));
    }

    // ── 운영자 ──────────────────────────────────────────────────────────────

    /**
     * 승인 — 발송 전 취소는 그 자리에서 취소·환불하고 끝(COMPLETED), 반품은 회수 안내(APPROVED).
     *
     * @param expectedAmount 화면이 확인한 금액(미리 보기와 다를 때 새 값 — 결정 72), 같으면 비워도 된다
     */
    public void approve(long requestId, Long expectedAmount, long operatorId, String ip) {
        tx.executeWithoutResult(status -> {
            Instant now = now();
            RequestRow before = requests.find(requestId).orElseThrow(GroupBuyException::requestNotFound);
            ParticipationRow p = participations.lock(before.participationId()).orElseThrow();
            RequestRow r = requests.lock(requestId).orElseThrow();
            if (r.status() != RequestStatus.REQUESTED) {
                throw GroupBuyException.requestNotOpen();
            }
            if (refundStore.paymentUnreconciled(p.paymentOrderId())) {
                throw GroupBuyException.paymentUnreconciled();
            }
            if (r.type() == RequestType.RETURN) {
                requests.transition(r.id(), EnumSet.of(RequestStatus.REQUESTED), RequestStatus.APPROVED, operatorId, now);
                events.publishEvent(new GroupBuyEvents.RequestChanged(r.id(), p.id(), r.userId(), RequestStatus.APPROVED.name(), null));
                return;
            }
            if (!EnumSet.of(ParticipationStatus.CONFIRMED, ParticipationStatus.PREPARING).contains(p.status())) {
                throw GroupBuyException.orderState();
            }
            GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow();
            List<ItemRow> items = participations.items(p.id());
            List<GroupBuyRefundCalculator.Line> calc = requestLines(r);
            long amount = GroupBuyRefundCalculator.cancel(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(g), held(p, items), calc, true).total();
            confirmAmount(r.previewAmount(), amount, expectedAmount);
            Map<Long, Integer> plan = new LinkedHashMap<>();
            calc.forEach(l -> plan.put(l.itemId(), l.qty()));
            // 먼저 끝낸다 — 주문 전체 취소면 열린 요청을 닫는 길(SUPERSEDED)이 이 요청을 건드리지 않게
            requests.transition(r.id(), EnumSet.of(RequestStatus.REQUESTED), RequestStatus.COMPLETED, operatorId, now);
            ParticipationCancelService.CancelResult result = cancels.cancelLines(p, plan, RefundCause.REQUEST, true, new GroupBuyRefundService.Extra(r.id(), 0, 0),
                    operatorId, "REQUEST " + r.id());
            audit.record(operatorId, AuditActions.GROUPBUY_REQUEST_APPROVE, GroupBuyAudit.TARGET_REQUEST, r.id(), ip, Map.of("type", "CANCEL", "amount", result.refundAmount(),
                    "previewAmount", r.previewAmount()));
            events.publishEvent(new GroupBuyEvents.RequestChanged(r.id(), p.id(), r.userId(), RequestStatus.COMPLETED.name(), null));
        });
    }

    /** 회수 송장(업체회수 — 택배사 회수 API 는 범위 밖, 운영자가 접수 뒤 입력) */
    public void recordPickup(long requestId, String carrierCode, String trackingNo, long operatorId) {
        String carrier = fulfillment.requireCarrier(carrierCode);
        String tracking = FulfillmentService.normalizeTracking(trackingNo);
        tx.executeWithoutResult(status -> {
            RequestRow r = requests.lock(requestId).orElseThrow(GroupBuyException::requestNotFound);
            if (r.type() != RequestType.RETURN || !EnumSet.of(RequestStatus.REQUESTED, RequestStatus.APPROVED).contains(r.status())) {
                throw GroupBuyException.requestNotOpen();
            }
            requests.setPickup(r.id(), carrier, tracking, now());
        });
    }

    /** 회수 확인(돌려받은 날 — 3영업일 환급 기한의 시작) */
    public void receive(long requestId, long operatorId) {
        tx.executeWithoutResult(status -> {
            RequestRow r = requests.lock(requestId).orElseThrow(GroupBuyException::requestNotFound);
            if (r.type() != RequestType.RETURN || !requests.markReceived(r.id(), now())) {
                throw GroupBuyException.requestNotOpen();
            }
            events.publishEvent(new GroupBuyEvents.RequestChanged(r.id(), r.participationId(), r.userId(), RequestStatus.RECEIVED.name(), null));
        });
    }

    /**
     * 반품 완료(환불) — 회수 확인 뒤. 줄마다 처리 방법과 수량(합 = 줄 수량), 보상액(고객발송 하자만, 상한 — 고액 문턱 누계에 든다).
     *
     * @param largeAllowed 부르는 운영자가 고액 환불 권한({@code commerce.refund.large})을 가졌는가
     */
    public void complete(long requestId, List<DispositionInput> dispositions, long compensation, Long expectedAmount, boolean largeAllowed, long operatorId, String ip) {
        tx.executeWithoutResult(status -> {
            Instant now = now();
            RequestRow before = requests.find(requestId).orElseThrow(GroupBuyException::requestNotFound);
            ParticipationRow p = participations.lock(before.participationId()).orElseThrow();
            RequestRow r = requests.lock(requestId).orElseThrow();
            if (r.type() != RequestType.RETURN || r.resolution() != Resolution.REFUND || !EnumSet.of(RequestStatus.APPROVED, RequestStatus.RECEIVED).contains(r.status())) {
                throw GroupBuyException.requestNotOpen();
            }
            if (refundStore.paymentUnreconciled(p.paymentOrderId())) {
                throw GroupBuyException.paymentUnreconciled();
            }
            GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow();
            List<ItemRow> items = participations.items(p.id());
            List<GroupBuyRequestStore.ItemRow> lines = requests.items(r.id());
            long cap = groups.settings().compensationCap();
            checkCompensation(r, compensation, cap);
            checkLarge(p, compensation, largeAllowed);
            List<GroupBuyRefundCalculator.Line> calcLines = requestLines(r);
            checkAddonOnly(items, calcLines);
            GroupBuyRefundCalculator.Result calc = GroupBuyRefundCalculator.returnRefund(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(g), held(p, items),
                    calcLines, r.method());
            confirmAmount(r.previewAmount(), calc.total(), expectedAmount);
            Map<Long, Map<ReturnDisposition, Integer>> plan = dispositionPlan(lines, dispositions, g, false);
            // 수량 — 반품 수량 먼저(다시 판매는 반품 수량 안에서만), 처리 방법마다 이동 줄
            if (r.status() == RequestStatus.APPROVED) {
                requests.markReceived(r.id(), now);
            }
            applyReturnQty(p, g, items, lines, plan, operatorId, now);
            // 돈 — 반품비·재부과가 상품값보다 크면 낸 부족분이 메운다(결정 68)
            long total = calc.total();
            long feeHeld = r.feeHeld();
            long covered = 0;
            long reshippingFee = calc.reshippingFee();
            long returnFee = calc.returnFee();
            if (total < 0) {
                long shortfall = -total;
                covered = Math.min(feeHeld, shortfall);
                long writeOff = shortfall - covered; // 낸 돈보다 모자라면 실제로 받은 만큼만 남긴다
                long cut = Math.min(writeOff, reshippingFee);
                reshippingFee -= cut;
                returnFee -= writeOff - cut;
            }
            GroupBuyRefundService.Parts parts = new GroupBuyRefundService.Parts(calc.itemRefund(), calc.shippingRefund(), reshippingFee, calc.reshippingRefund(), returnFee, 0,
                    covered);
            long compensationPg = Math.min(compensation, Math.max(0, p.remainingCash() - parts.amount()));
            long compensationBank = compensation - compensationPg;
            parts = parts.withCompensation(compensationPg);
            long refundId = refunds.refund(p, RefundCause.REQUEST, parts, new GroupBuyRefundService.Extra(r.id(), compensationBank, compensation), linesJson(calc), operatorId,
                    "REQUEST " + r.id(), now);
            if (compensationBank > 0) {
                transfers.insert(ids.nextId(), "GROUP_BUY_COMPENSATION", refundId, p.id(), p.userId(), compensationBank, "보상액 중 남은 결제액을 넘는 몫", operatorId, now);
            }
            if (compensation > 0) {
                requests.setCompensation(r.id(), compensation, now);
            }
            refundFeeHeldExcept(p, r, covered, operatorId, now);
            participations.addReshippingDelta(p.id(), reshippingFee - calc.reshippingRefund(), now);
            for (GroupBuyRequestStore.ItemRow line : lines) {
                calc.lines().stream().filter(a -> a.itemId() == line.participationItemId()).findFirst()
                        .ifPresent(a -> requests.setItemAmount(line.id(), a.amount(), a.priorOffset()));
            }
            if (calc.whole()) {
                // 받은 뒤 전부 반품 — 반품비가 남아 환불 누계가 결제액에 못 닿아도 REFUNDED(결정 46)
                participations.transition(p.id(), EnumSet.of(ParticipationStatus.DELIVERED), ParticipationStatus.REFUNDED, now);
                participations.insertHistory(ids.nextId(), p, ParticipationStatus.DELIVERED, ParticipationStatus.REFUNDED, "RETURN", operatorId, now);
            }
            requests.transition(r.id(), EnumSet.of(RequestStatus.APPROVED, RequestStatus.RECEIVED), RequestStatus.COMPLETED, operatorId, now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("type", "RETURN");
            detail.put("amount", parts.amount());
            detail.put("previewAmount", r.previewAmount());
            detail.put("compensation", compensation);
            audit.record(operatorId, AuditActions.GROUPBUY_REQUEST_APPROVE, GroupBuyAudit.TARGET_REQUEST, r.id(), ip, detail);
            audit.record(operatorId, AuditActions.GROUPBUY_RETURN_DISPOSE, GroupBuyAudit.TARGET_REQUEST, r.id(), ip, Map.of("dispositions", dispositionsDetail(plan)));
            events.publishEvent(new GroupBuyEvents.RequestChanged(r.id(), p.id(), r.userId(), RequestStatus.COMPLETED.name(), null));
        });
    }

    /**
     * 교환 재발송(결정 54) — 회수 확인 뒤 같은 주문에 다시 보낸다. 환불 0, 판매 수량 그대로(교환 출고 기록), 돌려받은 물건은 처리 방법(다시 판매 없음)으로 기록,
     * 고객발송이었으면 보상액만 돌려준다. 에스크로 배송 정보도 다시 등록.
     */
    public void exchangeShip(long requestId, String carrierCode, String trackingNo, List<DispositionInput> dispositions, long compensation, boolean largeAllowed,
                             long operatorId, String ip) {
        Long shipmentId = tx.execute(status -> {
            Instant now = now();
            RequestRow before = requests.find(requestId).orElseThrow(GroupBuyException::requestNotFound);
            ParticipationRow p = participations.lock(before.participationId()).orElseThrow();
            RequestRow r = requests.lock(requestId).orElseThrow();
            if (r.resolution() != Resolution.EXCHANGE || !EnumSet.of(RequestStatus.APPROVED, RequestStatus.RECEIVED).contains(r.status())) {
                throw GroupBuyException.requestNotOpen();
            }
            GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow();
            List<ItemRow> items = participations.items(p.id());
            List<GroupBuyRequestStore.ItemRow> lines = requests.items(r.id());
            checkCompensation(r, compensation, groups.settings().compensationCap());
            checkLarge(p, compensation, largeAllowed);
            Map<Long, Map<ReturnDisposition, Integer>> plan = dispositionPlan(lines, dispositions, g, true);
            if (r.status() == RequestStatus.APPROVED) {
                requests.markReceived(r.id(), now);
            }
            for (GroupBuyRequestStore.ItemRow line : lines) {
                ItemRow item = items.stream().filter(i -> i.id() == line.participationItemId()).findFirst().orElseThrow();
                for (Map.Entry<ReturnDisposition, Integer> d : plan.get(line.id()).entrySet()) {
                    requests.insertDisposition(ids.nextId(), line.id(), d.getKey().name(), d.getValue(), operatorId, now);
                    qty.recordReturn(g.id(), item, d.getValue(), d.getKey().name(), operatorId, now);
                }
                qty.exchangeOut(g.id(), item, line.qty(), operatorId, now);
                participations.exchangeItemQty(item.id(), line.qty());
            }
            long id = fulfillment.shipExchange(p, r.id(), carrierCode, trackingNo, operatorId, now);
            if (compensation > 0) {
                long pg = Math.min(compensation, p.remainingCash());
                long refundId = refunds.refund(p, RefundCause.REQUEST, new GroupBuyRefundService.Parts(0, 0, 0, 0, 0, pg, 0), new GroupBuyRefundService.Extra(r.id(),
                        compensation - pg, compensation), null, operatorId, "EXCHANGE " + r.id(), now);
                if (compensation > pg) {
                    transfers.insert(ids.nextId(), "GROUP_BUY_COMPENSATION", refundId, p.id(), p.userId(), compensation - pg, "보상액 중 남은 결제액을 넘는 몫", operatorId, now);
                }
                requests.setCompensation(r.id(), compensation, now);
            }
            requests.transition(r.id(), EnumSet.of(RequestStatus.APPROVED, RequestStatus.RECEIVED), RequestStatus.COMPLETED, operatorId, now);
            audit.record(operatorId, AuditActions.GROUPBUY_RETURN_DISPOSE, GroupBuyAudit.TARGET_REQUEST, r.id(), ip, Map.of("dispositions", dispositionsDetail(plan),
                    "exchange", true, "compensation", compensation));
            events.publishEvent(new GroupBuyEvents.RequestChanged(r.id(), p.id(), r.userId(), RequestStatus.COMPLETED.name(), "EXCHANGE_SHIPPED"));
            return id;
        });
        escrow.runFor(List.of(shipmentId));
    }

    /** 교환할 물건이 없거나 공구가 끝났다 — 환불로 바꾼다(회원 알림, 결정 54). 미리 보기 금액을 환불 계산으로 다시 적는다. 그 뒤 {@link #complete} */
    public void convertToRefund(long requestId, long operatorId) {
        tx.executeWithoutResult(status -> {
            RequestRow before = requests.find(requestId).orElseThrow(GroupBuyException::requestNotFound);
            ParticipationRow p = participations.lock(before.participationId()).orElseThrow();
            RequestRow r = requests.lock(requestId).orElseThrow();
            if (!requests.toRefund(r.id(), now())) {
                throw GroupBuyException.requestNotOpen();
            }
            GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow();
            long amount = GroupBuyRefundCalculator.returnRefund(GroupBuyRefundCalculator.order(p), GroupBuyRefundCalculator.policy(g), held(p, participations.items(p.id())),
                    requestLines(r), r.method()).total();
            requests.setPreview(r.id(), amount, now());
            events.publishEvent(new GroupBuyEvents.RequestChanged(r.id(), r.participationId(), r.userId(), r.status().name(), "EXCHANGE_TO_REFUND"));
        });
    }

    /**
     * 거절(결정 55) — 사유 코드 필수. 반품은 근거 사진 1~3장, 가치 감소 사유는 상품에 청약철회 제한 안내가 있고 업체 부담 요청이 아닐 때만, 「이미 발송함」은 쓸 수 없다.
     * 발송 전 취소 요청은 「이미 발송함」만 — 그 송장으로 같은 트랜잭션에서 발송 처리한다(10-3).
     */
    public void reject(long requestId, RejectReason reason, String note, List<Long> photoIds, String carrierCode, String trackingNo, long operatorId, String ip) {
        if (reason == null) {
            throw GroupBuyException.rejectReasonNotAllowed();
        }
        String memo = note == null || note.isBlank() ? null : note.strip();
        if (memo != null && memo.length() > 500) {
            throw ValidationException.of("note", new MessageRef("groupbuy.request.memo.invalid"));
        }
        List<Long> photos = photoIds == null ? List.of() : List.copyOf(new HashSet<>(photoIds));
        Long shipmentId = tx.execute(status -> {
            Instant now = now();
            RequestRow before = requests.find(requestId).orElseThrow(GroupBuyException::requestNotFound);
            ParticipationRow p = participations.lock(before.participationId()).orElseThrow();
            RequestRow r = requests.lock(requestId).orElseThrow();
            if (!EnumSet.of(RequestStatus.REQUESTED, RequestStatus.APPROVED, RequestStatus.RECEIVED).contains(r.status())) {
                throw GroupBuyException.requestNotOpen();
            }
            Long shipped = null;
            if (r.type() == RequestType.CANCEL) {
                if (reason != RejectReason.ALREADY_SHIPPED) {
                    throw GroupBuyException.rejectReasonNotAllowed();
                }
                requests.reject(r.id(), RequestStatus.OPEN, reason.name(), memo, operatorId, now);
                shipped = fulfillment.shipOne(new FulfillmentService.ShipLine(p.id(), carrierCode, trackingNo), operatorId);
            } else {
                GroupBuyRow g = groups.find(p.groupBuyId()).orElseThrow();
                boolean restrictionShown = g.withdrawalRestriction() != null && !g.withdrawalRestriction().isBlank();
                if (reason == RejectReason.ALREADY_SHIPPED || (reason.needsRestrictionNotice() && (!restrictionShown || r.sellerFault()))) {
                    throw GroupBuyException.rejectReasonNotAllowed();
                }
                if (photos.isEmpty() || photos.size() > PHOTO_MAX) {
                    throw GroupBuyException.rejectReasonNotAllowed(); // 다툼은 사업자가 증명한다(제17조 ⑤)
                }
                if (requests.attachPhotos(r.id(), "OPERATOR", operatorId, photos, now) != photos.size()) {
                    throw ValidationException.of("photoIds", new MessageRef("groupbuy.request.photos.invalid"));
                }
                requests.reject(r.id(), RequestStatus.OPEN, reason.name(), memo, operatorId, now);
            }
            refundFeeHeld(p, r, operatorId, "REJECTED", now);
            Map<String, Object> detail = new LinkedHashMap<>(Map.of("reasonCode", reason.name(), "photos", photos.size()));
            if (r.defectKnownOn() != null) {
                detail.put("defectKnownOn", r.defectKnownOn().toString()); // 회원이 적은 「하자를 안 날」을 보고 거절했다(제17조 ③)
            }
            audit.record(operatorId, AuditActions.GROUPBUY_REQUEST_REJECT, GroupBuyAudit.TARGET_REQUEST, r.id(), ip, detail);
            events.publishEvent(new GroupBuyEvents.RequestChanged(r.id(), p.id(), r.userId(), RequestStatus.REJECTED.name(), reason.name()));
            return shipped;
        });
        if (shipmentId != null) {
            escrow.runFor(List.of(shipmentId));
        }
    }

    /** 처리 기한 단계(P35 — 발송 전 취소는 요청한 날, 반품은 돌려받은 날부터 영업일). 끝난 요청·돌려받기 전 반품은 NONE */
    public GroupBuyBusinessDays.Level deadlineLevel(long requestId) {
        RequestRow r = requests.find(requestId).orElseThrow(GroupBuyException::requestNotFound);
        if (!RequestStatus.OPEN.contains(r.status()) || r.status() == RequestStatus.AWAITING_FEE) {
            return GroupBuyBusinessDays.Level.NONE;
        }
        Instant start = r.type() == RequestType.CANCEL ? r.createdAt() : r.receivedAt();
        return start == null ? GroupBuyBusinessDays.Level.NONE : GroupBuyBusinessDays.deadlineLevel(start, now(), groups.settings().holidays());
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private void applyReturnQty(ParticipationRow p, GroupBuyRow g, List<ItemRow> items, List<GroupBuyRequestStore.ItemRow> lines,
                                Map<Long, Map<ReturnDisposition, Integer>> plan, long operatorId, Instant now) {
        for (GroupBuyRequestStore.ItemRow line : lines) {
            ItemRow item = items.stream().filter(i -> i.id() == line.participationItemId()).findFirst().orElseThrow();
            if (!participations.returnItemQty(item.id(), line.qty())) {
                throw GroupBuyException.orderState(); // 그새 취소·반품돼 가진 수량이 모자라다(결정 83)
            }
            for (Map.Entry<ReturnDisposition, Integer> d : plan.get(line.id()).entrySet()) {
                requests.insertDisposition(ids.nextId(), line.id(), d.getKey().name(), d.getValue(), operatorId, now);
                if (d.getKey() == ReturnDisposition.RESELL) {
                    if (!participations.restockItemQty(item.id(), d.getValue())) {
                        throw GroupBuyException.notResellable();
                    }
                    qty.restock(g.id(), item, d.getValue(), operatorId, now);
                } else {
                    qty.recordReturn(g.id(), item, d.getValue(), d.getKey().name(), operatorId, now);
                }
            }
        }
    }

    /** 요청 줄마다 처리 방법 → 수량(합 = 줄 수량, 다시 판매는 모집 중 + 단순 변심만 — 결정 15) */
    private static Map<Long, Map<ReturnDisposition, Integer>> dispositionPlan(List<GroupBuyRequestStore.ItemRow> lines, List<DispositionInput> input, GroupBuyRow g,
                                                                               boolean exchange) {
        Map<Long, Map<ReturnDisposition, Integer>> out = new LinkedHashMap<>();
        for (GroupBuyRequestStore.ItemRow line : lines) {
            out.put(line.id(), new EnumMap<>(ReturnDisposition.class));
        }
        for (DispositionInput d : input == null ? List.<DispositionInput>of() : input) {
            GroupBuyRequestStore.ItemRow line = lines.stream().filter(l -> l.skuId() == d.skuId()).findFirst().orElse(null);
            if (line == null || d.disposition() == null || d.qty() < 1) {
                throw ValidationException.of("dispositions", new MessageRef("groupbuy.request.dispositions.invalid"));
            }
            if (d.disposition() == ReturnDisposition.RESELL && (exchange || line.reason() != RequestReason.CHANGE_OF_MIND || g.status() != GroupBuyStatus.RECRUITING)) {
                throw GroupBuyException.notResellable();
            }
            out.get(line.id()).merge(d.disposition(), d.qty(), Integer::sum);
        }
        for (GroupBuyRequestStore.ItemRow line : lines) {
            int sum = out.get(line.id()).values().stream().mapToInt(Integer::intValue).sum();
            if (sum != line.qty()) {
                throw ValidationException.of("dispositions", new MessageRef("groupbuy.request.dispositions.invalid"));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> dispositionsDetail(Map<Long, Map<ReturnDisposition, Integer>> plan) {
        List<Map<String, Object>> out = new ArrayList<>();
        plan.forEach((lineId, m) -> m.forEach((d, q) -> out.add(Map.of("line", Long.toString(lineId), "disposition", d.name(), "qty", q))));
        return out;
    }

    /** 보상액 — 고객발송 + 업체 부담 반품만, 상한(설정) 안 */
    private static void checkCompensation(RequestRow r, long compensation, long cap) {
        if (compensation < 0) {
            throw ValidationException.of("compensationAmount", new MessageRef("groupbuy.refund.amount.invalid"));
        }
        if (compensation > 0 && (!r.sellerFault() || r.method() != CollectionMethod.CUSTOMER_SEND)) {
            throw ValidationException.of("compensationAmount", new MessageRef("groupbuy.refund.amount.invalid"));
        }
        if (compensation > cap) {
            throw GroupBuyException.compensationOverCap(cap);
        }
    }

    /** 운영자가 금액을 정한 환불(보상)의 그 주문 누계가 고액 문턱 이상이면 고액 권한(결정 70 — 0 = 끔) */
    private void checkLarge(ParticipationRow p, long operatorAmount, boolean largeAllowed) {
        long threshold = groups.settings().largeRefundThreshold();
        if (operatorAmount > 0 && threshold > 0 && !largeAllowed && refundStore.operatorAmountTotal(p.id()) + operatorAmount >= threshold) {
            throw GroupBuyException.refundLargeRequired(threshold);
        }
    }

    /** 미리 보기와 다르면 화면이 새 값을 확인해 보내야 한다(결정 72) */
    private static void confirmAmount(long preview, long current, Long expected) {
        if (current != preview && (expected == null || expected != current)) {
            throw GroupBuyException.refundAmountChanged(preview, current);
        }
    }

    private void refundFeeHeld(ParticipationRow p, RequestRow r, Long operatorId, String reason, Instant now) {
        if (r.feeHeld() > 0 && r.feePaymentOrderId() != null) {
            refunds.refundReturnFee(p, r.id(), r.feePaymentOrderId(), r.feeHeld(), operatorId, reason, now);
        }
    }

    /** 낸 부족분 중 메운 몫({@code covered})을 뺀 나머지를 돌려준다 */
    private void refundFeeHeldExcept(ParticipationRow p, RequestRow r, long covered, Long operatorId, Instant now) {
        long rest = r.feeHeld() - covered;
        if (rest > 0 && r.feePaymentOrderId() != null) {
            refunds.refundReturnFee(p, r.id(), r.feePaymentOrderId(), rest, operatorId, "COMPLETED", now);
        }
    }

    /**
     * 반품 기한(한국 날짜) — 단순 변심·기타 줄이 있으면 배송완료 7일, 업체 부담 줄만이면 3개월, 3개월이 지나면 「하자를 안 날」부터 30일(제17조 ③).
     * 안 날은 배송완료 날 ~ 오늘이어야 한다(미래·배송 전은 400). 남길 안 날(업체 부담 줄만일 때)을 돌려준다.
     */
    private LocalDate checkReturnWindow(ParticipationRow p, List<GroupBuyRefundCalculator.Line> lines, LocalDate knownOn, Instant now) {
        LocalDate delivered = ServiceTime.dateOf(p.deliveredAt());
        LocalDate today = ServiceTime.dateOf(now);
        if (knownOn != null && (knownOn.isBefore(delivered) || knownOn.isAfter(today))) {
            throw ValidationException.of("defectKnownOn", new MessageRef("groupbuy.request.defectKnownOn.invalid"));
        }
        boolean customerLine = lines.stream().anyMatch(l -> !l.reason().sellerFault());
        if (customerLine) {
            if (today.isAfter(delivered.plusDays(CHANGE_OF_MIND_DAYS))) {
                throw GroupBuyException.returnWindowClosed();
            }
            return null;
        }
        if (today.isAfter(delivered.plusMonths(SELLER_FAULT_MONTHS)) && (knownOn == null || today.isAfter(knownOn.plusDays(SELLER_FAULT_KNOWN_DAYS)))) {
            throw GroupBuyException.returnWindowClosed();
        }
        return knownOn;
    }

    /** 요청 줄 입력 → 주문 줄(단품 하나에 줄 하나 — 유일 (주문, 단품)). 반품은 사유 필수, 수량은 가진 수량 안 */
    private static List<GroupBuyRefundCalculator.Line> toLines(List<ItemRow> items, RequestType type, List<LineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            throw ValidationException.of("items", new MessageRef("groupbuy.request.lines.invalid"));
        }
        List<GroupBuyRefundCalculator.Line> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (LineInput line : lines) {
            ItemRow item = items.stream().filter(i -> i.skuId() == line.skuId()).findFirst().orElse(null);
            if (item == null || line.qty() < 1 || line.qty() > item.heldQty() || !seen.add(item.id()) || (type == RequestType.RETURN && line.reason() == null)) {
                throw ValidationException.of("items", new MessageRef("groupbuy.request.lines.invalid"));
            }
            out.add(new GroupBuyRefundCalculator.Line(item.id(), line.qty(), type == RequestType.RETURN ? line.reason() : null));
        }
        return out;
    }

    private List<GroupBuyRefundCalculator.Line> requestLines(RequestRow r) {
        return requests.items(r.id()).stream().map(i -> new GroupBuyRefundCalculator.Line(i.participationItemId(), i.qty(), r.type() == RequestType.RETURN ? i.reason() : null))
                .toList();
    }

    /** 본 상품을 모두 빼며 추가 구성상품만 남기면 409(결정 88) */
    private static void checkAddonOnly(List<ItemRow> items, List<GroupBuyRefundCalculator.Line> lines) {
        Map<Long, Integer> plan = new LinkedHashMap<>();
        lines.forEach(l -> plan.merge(l.itemId(), l.qty(), Integer::sum));
        int main = 0;
        List<String> addons = new ArrayList<>();
        for (ItemRow i : items) {
            int left = i.heldQty() - plan.getOrDefault(i.id(), 0);
            if (i.main()) {
                main += left;
            } else if (left > 0) {
                addons.add(Long.toString(i.skuId()));
            }
        }
        if (main == 0 && !addons.isEmpty()) {
            throw GroupBuyException.addonOnly(addons);
        }
    }

    /** 계산기에 넘길 지금 줄 — 금액만 돌려준 운영 환불의 남은 몫 포함(결정 71) */
    private List<GroupBuyRefundCalculator.Held> held(ParticipationRow p, List<ItemRow> items) {
        Map<Long, Long> prior = participations.priorRefundLeft(p.id());
        return items.stream().map(i -> new GroupBuyRefundCalculator.Held(i.id(), i.skuId(), i.main(), i.unitPrice(), i.heldQty(), Math.max(0, prior.getOrDefault(i.id(), 0L))))
                .toList();
    }

    private static String linesJson(GroupBuyRefundCalculator.Result calc) {
        StringJoiner j = new StringJoiner(",", "[", "]");
        calc.lines().forEach(l -> j.add("{\"itemId\":\"" + l.itemId() + "\",\"qty\":" + l.qty() + ",\"amount\":" + l.amount() + ",\"priorOffset\":" + l.priorOffset() + "}"));
        return j.toString();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
