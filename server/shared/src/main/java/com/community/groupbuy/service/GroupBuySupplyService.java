package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.entity.SupplyChoice;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyRequestStore;
import com.community.groupbuy.repository.master.GroupBuyRequestStore.SupplyChoiceRow;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 단품 공급 불가(계약 결정 53 — 사용자 결정: 답이 없으면 나머지만 받기). 공구 행을 잠그는 일과 결제 행을 잠그는 일을 한 트랜잭션에 넣지 않는다(결정 37):
 * ① 공급 불가 기록 + 그 단품 판매 중지 ② 주문마다 새 트랜잭션 — 그 단품의 발송 전 줄 전부 취소·환불(원인 {@code SUPPLY_FAILURE}, 배송비 재계산 없음 — 사업자 귀책),
 * 줄이 모두 빠진 주문은 배송비 포함 전액, 줄이 남은 주문은 「나머지만 받기 / 전체 취소」를 묻는다(기한 = 설정 영업일) ③ 그 단품 최대를 확정 + 선점으로(줄이기 하한 예외).
 * 기한 전에 답하지 않은 주문은 발송하지 않고, 기한이 지나면 「나머지만 받기」({@link #applyDefaults}).
 */
@Service
public class GroupBuySupplyService {

    public record Result(long supplyFailureId, int refundedOrders, int askedOrders) {
    }

    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final GroupBuyRequestStore requests;
    private final ParticipationCancelService cancels;
    private final GroupBuyQtyService qty;
    private final GroupBuyAudit audit;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GroupBuySupplyService(ParticipationStore participations, GroupBuyStore groups, GroupBuyRequestStore requests, ParticipationCancelService cancels,
                                 GroupBuyQtyService qty, GroupBuyAudit audit, SnowflakeIdGenerator ids, ApplicationEventPublisher events,
                                 PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.participations = participations;
        this.groups = groups;
        this.requests = requests;
        this.cancels = cancels;
        this.qty = qty;
        this.audit = audit;
        this.ids = ids;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 단품 공급 불가 처리 — 같은 단품은 한 번(409 {@code GROUP_BUY_INVALID_STATE}) */
    public Result markFailure(long groupBuyId, long skuId, String reason, long operatorId, String ip) {
        String why = GroupBuyAudit.reason(reason);
        long failureId = ids.nextId();
        Instant choiceUntil = tx.execute(status -> {
            Instant now = now();
            GroupBuyRow g = groups.find(groupBuyId).orElseThrow(GroupBuyException::notFound);
            groups.skus(groupBuyId).stream().filter(s -> s.id() == skuId).findFirst().orElseThrow(GroupBuyException::notFound);
            Instant until = GroupBuyBusinessDays.endOfBusinessDays(now, groups.settings().supplyChoiceBusinessDays(), groups.settings().holidays());
            try {
                requests.insertSupplyFailure(failureId, g.id(), skuId, why, until, operatorId, now);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw GroupBuyException.invalidState();
            }
            groups.stopSelling(skuId, groupBuyId);
            return until;
        });
        int refunded = 0;
        int asked = 0;
        for (ParticipationRow candidate : participations.ofGroup(groupBuyId, ParticipationStatus.PAID_BEFORE_SHIPPING)) {
            String outcome = tx.execute(status -> {
                Instant now = now();
                Optional<ParticipationRow> locked = participations.lock(candidate.id()).filter(p -> ParticipationStatus.PAID_BEFORE_SHIPPING.contains(p.status()));
                if (locked.isEmpty()) {
                    return "SKIP";
                }
                ParticipationRow p = locked.get();
                List<ItemRow> items = participations.items(p.id());
                Optional<ItemRow> line = items.stream().filter(i -> i.skuId() == skuId && i.heldQty() > 0).findFirst();
                if (line.isEmpty()) {
                    return "SKIP";
                }
                // 본 상품 줄이 모두 빠지면 추가상품만 남길 수 없다 — 그때는 주문 전체(배송비 포함 전액)
                boolean onlyAddonsLeft = items.stream().filter(i -> i.main() && i.id() != line.get().id()).mapToInt(ItemRow::heldQty).sum() == 0;
                Map<Long, Integer> plan = onlyAddonsLeft ? ParticipationCancelService.wholeOrder(items) : Map.of(line.get().id(), line.get().heldQty());
                ParticipationCancelService.CancelResult r = cancels.cancelLines(p, plan, RefundCause.SUPPLY_FAILURE, false, GroupBuyRefundService.Extra.NONE, operatorId, why);
                if (r.wholeOrder()) {
                    return "REFUNDED";
                }
                requests.insertSupplyChoice(ids.nextId(), failureId, p.id(), p.userId(), choiceUntil, now);
                events.publishEvent(new GroupBuyEvents.SupplyChoiceAsked(p.id(), p.userId(), skuId, choiceUntil));
                return "ASKED";
            });
            if ("REFUNDED".equals(outcome)) {
                refunded++;
            } else if ("ASKED".equals(outcome)) {
                asked++;
            }
        }
        int refundedOrders = refunded;
        int askedOrders = asked;
        tx.executeWithoutResult(status -> {
            qty.reduceForSupplyFailure(groupBuyId, skuId, "SUPPLY_FAILURE", operatorId, now());
            audit.record(operatorId, AuditActions.GROUPBUY_SUPPLY_FAILURE, GroupBuyAudit.TARGET_GROUP_BUY, groupBuyId, ip, Map.of("skuId", Long.toString(skuId), "reason", why,
                    "refundedOrders", refundedOrders, "askedOrders", askedOrders));
        });
        return new Result(failureId, refunded, asked);
    }

    /** 회원의 답 — 기한 전 한 번. 전체 취소면 남은 줄도 배송비 포함 전액(사업자 귀책 — 재계산 없음) */
    public void choose(long userId, long participationId, SupplyChoice choice) {
        tx.executeWithoutResult(status -> {
            Instant now = now();
            ParticipationRow p = participations.lock(participationId).filter(r -> r.userId() == userId).orElseThrow(GroupBuyException::orderNotFound);
            List<SupplyChoiceRow> open = requests.openChoices(p.id());
            if (open.isEmpty()) {
                throw GroupBuyException.supplyChoiceClosed();
            }
            for (SupplyChoiceRow c : open) {
                if (!requests.decideChoice(c.id(), choice, false, now)) {
                    throw GroupBuyException.supplyChoiceClosed();
                }
            }
            if (choice == SupplyChoice.CANCEL_ALL) {
                if (!ParticipationStatus.PAID_BEFORE_SHIPPING.contains(p.status())) {
                    throw GroupBuyException.orderState();
                }
                cancels.cancelLines(p, ParticipationCancelService.wholeOrder(participations.items(p.id())), RefundCause.SUPPLY_FAILURE, false, GroupBuyRefundService.Extra.NONE,
                        null, "SUPPLY_CHOICE");
            }
        });
    }

    /** 기한이 지났는데 답하지 않은 주문 → 나머지만 받기(defaulted). 처리한 수 */
    public int applyDefaults() {
        int n = 0;
        for (long id : requests.choicesDue(now(), 200)) {
            Boolean done = tx.execute(status -> requests.decideChoice(id, SupplyChoice.KEEP_REST, true, now()));
            if (Boolean.TRUE.equals(done)) {
                n++;
            }
        }
        return n;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
