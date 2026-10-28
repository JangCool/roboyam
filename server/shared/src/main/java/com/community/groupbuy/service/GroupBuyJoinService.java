package com.community.groupbuy.service;

import com.community.groupbuy.dto.GroupBuyBuyer;
import com.community.groupbuy.dto.JoinRequest;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.dto.Quote;
import com.community.groupbuy.dto.ShippingAddress;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyMemberStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentOrderRequest;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.service.PaymentOrderService;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공구 주문(참여)과 결제 다시 열기(commerce.md 10-6, 계약 결정 23·28·37·41·63·72·85·86). 멱등 키·속도 제한({@code GROUPBUY_JOIN} 1분 5번)은 api 의
 * {@code MoneyRequestGuard} 가 감싼다(컨트롤러는 2b-4).
 *
 * <ol>
 *   <li>입력(동의 3종 — 기본 체크 없음, 줄·수량·배송지) → 자격({@link GroupBuyEligibility}) → 본인의 지난 선점 정리(본인 주문만 — 결정 85, 따로 확정).</li>
 *   <li>한 트랜잭션: (공구, 회원) 어드바이저리 잠금(<b>모든 행 잠금보다 먼저</b>) → 진행 중 주문 1건 → 모집 중·기간 → 1인 한도(차수 합산 선택 — 결정 24·86) → 금액
 *       (화면이 본 금액과 다르면 409 {@code GROUP_BUY_AMOUNT_CHANGED} — 결정 72) → 배송지 암호화 → 주문 PENDING_PAYMENT + 줄 → 줄마다 선점(공구 행 → 옵션 ID 오름차순 — 하나라도 모자라면
 *       주문 전체 실패) → 결제 주문({@code PaymentOrderService.create}, 유효 시각 = 선점 끝 — 결정 41).</li>
 * </ol>
 */
@Service
public class GroupBuyJoinService {

    /** 배송지 칸 용도(AAD — 다른 칸에 옮긴 암호문은 풀리지 않는다) */
    public static final DataPurpose RECIPIENT = DataPurpose.of("groupbuy.address.recipient");
    public static final DataPurpose PHONE = DataPurpose.of("groupbuy.address.phone");
    public static final DataPurpose ADDRESS1 = DataPurpose.of("groupbuy.address.line1");
    public static final DataPurpose ADDRESS2 = DataPurpose.of("groupbuy.address.line2");
    public static final DataPurpose MEMO = DataPurpose.of("groupbuy.address.memo");

    /** 결제 모듈의 최소 유효 시간 — 남은 선점이 이보다 짧으면 결제를 다시 열지 않는다(결정 63) */
    public static final Duration MIN_PAYMENT_WINDOW = Duration.ofMinutes(1);
    static final int ORDER_NAME_MAX = 100;

    private final GroupBuyStore groups;
    private final ParticipationStore participations;
    private final GroupBuyMemberStore members;
    private final GroupBuyQtyService qty;
    private final GroupBuyQuoteService quotes;
    private final GroupBuyEligibility eligibility;
    private final HoldExpiryService holds;
    private final PaymentOrderService paymentOrders;
    private final DataCipher cipher;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GroupBuyJoinService(GroupBuyStore groups, ParticipationStore participations, GroupBuyMemberStore members, GroupBuyQtyService qty, GroupBuyQuoteService quotes,
                               GroupBuyEligibility eligibility, HoldExpiryService holds, PaymentOrderService paymentOrders, DataCipher cipher, SnowflakeIdGenerator ids,
                               PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.groups = groups;
        this.participations = participations;
        this.members = members;
        this.qty = qty;
        this.quotes = quotes;
        this.eligibility = eligibility;
        this.holds = holds;
        this.paymentOrders = paymentOrders;
        this.cipher = cipher;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 주문한다 — {@code POST /api/group-buys/{id}/orders}(2b-4).
     *
     * @throws ValidationException 동의·줄·배송지
     * @throws GroupBuyException   없음·모집 중 아님·다 팔림/잠시 품절·옵션 품절·한도·진행 중 주문·금액 바뀜·판매 꺼짐
     */
    public JoinResult join(GroupBuyBuyer buyer, JoinRequest request) {
        validate(request);
        eligibility.assertCanJoin(buyer);
        cipher.requireConfigured();
        holds.expireOwnOverdue(buyer.userId(), request.groupBuyId());
        try {
            return tx.execute(status -> joinInTx(buyer.userId(), request));
        } catch (DuplicateKeyException e) {
            throw GroupBuyException.alreadyJoined(); // 진행 중 주문 부분 유일 색인 — 어드바이저리 잠금 뒤 마지막 방어선(결정 66)
        }
    }

    private JoinResult joinInTx(long userId, JoinRequest request) {
        Instant now = now();
        members.lockMemberInGroupBuy(request.groupBuyId(), userId);
        GroupBuyRow group = groups.find(request.groupBuyId()).filter(g -> g.publishedAt() != null).orElseThrow(GroupBuyException::notFound);
        if (participations.inProgress(group.id(), userId).isPresent()) {
            throw GroupBuyException.alreadyJoined();
        }
        if (group.status() != com.community.groupbuy.entity.GroupBuyStatus.RECRUITING || now.isBefore(group.startAt()) || !now.isBefore(group.deadline())) {
            throw GroupBuyException.notRecruiting();
        }
        Quote quote = GroupBuyQuoteService.price(group, groups.options(group.id()), request.lines(), quotes.shippingOf(group));
        int requested = quote.lines().stream().mapToInt(Quote.Line::qty).sum();
        if (group.maxQtyPerUser() != null) {
            List<Long> scope = group.roundLimitShared() ? groups.roundIds(group.roundRootId()) : List.of(group.id());
            int used = participations.limitUsed(userId, scope);
            if (used + requested > group.maxQtyPerUser()) {
                throw GroupBuyException.limitExceeded(group.maxQtyPerUser() - used);
            }
        }
        if (request.expectedAmount() == null || request.expectedAmount() != quote.amount()) {
            throw GroupBuyException.amountChanged(quote.amount());
        }
        GroupBuyStore.Settings settings = groups.settings(); // 선점 시간은 이 트랜잭션의 지금 값(결정 19)
        Instant reservedUntil = now.plus(Duration.ofMinutes(settings.holdMinutes()));
        long participationId = ids.nextId();
        ShippingAddress a = request.address();
        participations.insert(new ParticipationStore.NewParticipation(participationId, participations.nextOrderNo(ServiceTime.dateOf(now)), group.id(), userId,
                quote.itemsAmount(), quote.shippingFee(), quote.amount(), group.revisionNo(), reservedUntil, cipher.encrypt(RECIPIENT, a.recipient().strip()),
                cipher.encrypt(PHONE, a.phone().strip()), a.postcode().strip(), cipher.encrypt(ADDRESS1, a.address1().strip()), enc(ADDRESS2, a.address2()),
                enc(MEMO, a.memo()), now));
        List<ItemRow> items = new ArrayList<>();
        for (Quote.Line line : quote.lines()) {
            long itemId = ids.nextId();
            participations.insertItem(itemId, participationId, group.id(), line.optionId(), line.qty(), line.unitPrice());
            items.add(new ItemRow(itemId, participationId, group.id(), line.optionId(), line.qty(), line.unitPrice(), line.itemAmount(), 0, 0, 0));
        }
        qty.reserve(group, participationId, items, now);
        ParticipationRow created = participations.find(participationId).orElseThrow();
        participations.insertHistory(ids.nextId(), created, null, ParticipationStatus.PENDING_PAYMENT, null, null, now);
        PaymentCheckout checkout = paymentOrders.create(new PaymentOrderRequest(userId, PaymentOrderType.GROUP_BUY, participationId, orderName(group), quote.amount(),
                Duration.between(now, reservedUntil)));
        // 선점 끝 = 결제 유효 시각(결정 41) — 결제 모듈이 정한 시각으로 맞춘다
        participations.attachPayment(participationId, checkout.paymentOrderId(), checkout.expiresAt(), now);
        return new JoinResult(participationId, created.orderNo(), quote.amount(), checkout.expiresAt(), checkout);
    }

    /**
     * 결제 다시 열기(결정 63) — 남은 선점이 1분 미만이면 409 {@code GROUP_BUY_HOLD_EXPIRED}. 아직 유효한 READY 결제가 있으면 새로 만들지 않고 그 결제창을 돌려준다
     * (두 번 눌러도 앞 결제가 늦은 결제가 되지 않게). 결제창이 실패한 뒤({@code afterFailure})면 새 결제 번호로 다시 만든다(포트원은 실패한 결제 ID 의 재사용을 보장하지 않는다 —
     * commerce.md 8-1). 선점을 늘리지 않는다 — 새 결제의 유효 시각도 같은 선점 끝(결정 41).
     * READY 결제를 닫는 흐름이라 결제 행을 먼저 잠근다(결정 37 — {@code create} 가 앞 READY 를 닫는다).
     */
    public PaymentCheckout reopen(long userId, long participationId, boolean afterFailure) {
        ParticipationRow p = participations.find(participationId).filter(row -> row.userId() == userId).orElseThrow(GroupBuyException::orderNotFound);
        Instant now = now();
        if (p.status() != ParticipationStatus.PENDING_PAYMENT || p.reservedUntil().isBefore(now.plus(MIN_PAYMENT_WINDOW))) {
            throw GroupBuyException.holdExpired();
        }
        return tx.execute(status -> {
            if (!afterFailure) {
                Optional<PaymentCheckout> open = paymentOrders.openCheckout(PaymentOrderType.GROUP_BUY, participationId, Duration.ZERO);
                if (open.isPresent()) {
                    return open.get();
                }
            }
            GroupBuyRow group = groups.find(p.groupBuyId()).orElseThrow(GroupBuyException::notFound);
            PaymentCheckout checkout = paymentOrders.create(new PaymentOrderRequest(userId, PaymentOrderType.GROUP_BUY, participationId, orderName(group), p.cashAmount(),
                    Duration.between(now, p.reservedUntil())));
            if (!participations.attachPayment(participationId, checkout.paymentOrderId(), p.reservedUntil(), now)) {
                status.setRollbackOnly(); // 그새 결제됐거나 닫혔다 — 새 결제를 만들지 않은 것으로 되돌린다
                throw GroupBuyException.holdExpired();
            }
            return checkout;
        });
    }

    private String enc(DataPurpose purpose, String value) {
        return value == null || value.isBlank() ? null : cipher.encrypt(purpose, value.strip());
    }

    private static String orderName(GroupBuyRow group) {
        String title = group.title().strip();
        return title.length() <= ORDER_NAME_MAX ? title : title.substring(0, ORDER_NAME_MAX);
    }

    /** 동의 3종(기본 체크 없음 — 결정 59)·줄·배송지 */
    static void validate(JoinRequest request) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (!request.agreePurchaseTerms()) {
            fields.put("agreePurchaseTerms", new MessageRef("groupbuy.order.agree.required"));
        }
        if (!request.agreeRefundPolicy()) {
            fields.put("agreeRefundPolicy", new MessageRef("groupbuy.order.agree.required"));
        }
        if (!request.agreePrivacy()) {
            fields.put("agreePrivacy", new MessageRef("groupbuy.order.agree.required"));
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            fields.put("lines", new MessageRef("groupbuy.order.lines.required"));
        }
        ShippingAddress a = request.address();
        if (a == null || blankOrLonger(a.recipient(), 50)) {
            fields.put("address.recipient", new MessageRef("groupbuy.order.address.invalid"));
        }
        if (a == null || a.phone() == null || !a.phone().strip().matches("[0-9-]{9,15}")) {
            fields.put("address.phone", new MessageRef("groupbuy.order.address.invalid"));
        }
        if (a == null || a.postcode() == null || !a.postcode().strip().matches("[0-9]{5,6}")) {
            fields.put("address.postcode", new MessageRef("groupbuy.order.address.invalid"));
        }
        if (a == null || blankOrLonger(a.address1(), 200)) {
            fields.put("address.address1", new MessageRef("groupbuy.order.address.invalid"));
        }
        if (a != null && a.address2() != null && a.address2().strip().length() > 200) {
            fields.put("address.address2", new MessageRef("groupbuy.order.address.invalid"));
        }
        if (a != null && a.memo() != null && a.memo().strip().length() > 200) {
            fields.put("address.memo", new MessageRef("groupbuy.order.address.invalid"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
    }

    private static boolean blankOrLonger(String value, int max) {
        return value == null || value.isBlank() || value.strip().length() > max;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
