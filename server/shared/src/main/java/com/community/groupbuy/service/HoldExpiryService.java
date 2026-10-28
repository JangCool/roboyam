package com.community.groupbuy.service;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.entity.ExpireCause;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.service.PaymentOrderService;
import com.community.restriction.entity.RestrictionAutoRule;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 중 주문 닫기(선점 만료 — commerce.md 10-6 4, 계약 결정 28·29·37) — 주문마다 <b>새 트랜잭션</b>:
 * <ol>
 *   <li><b>결제 행 먼저</b>: 그 주문의 열린 READY 결제를 EXPIRED 로({@link PaymentOrderService#expireOpen} — 0 행이어도 계속). 결제 확정이 결제 행 → 주문 차례라 같은 차례가 된다(교착 없음).</li>
 *   <li>주문 조건부 전이 PENDING_PAYMENT → EXPIRED(원인) — <b>이긴 쪽만</b> 선점 해제(RELEASE)·상태 이력(결제 확정·다른 워커와 이중 해제 없음).</li>
 *   <li>원인이 선점 시간 초과({@code HOLD_TIMEOUT})면 최근 24시간 HOLD_TIMEOUT 이 3번이 되면 24시간 공구 참여 제한(자동, 10-5 · 12-3). 다른 원인은 세지 않는다.</li>
 * </ol>
 * 닫은 뒤 결제가 오면 결제 모듈이 늦은 결제로 자동 취소한다(P15 — 살리지 않는다).
 */
@Service
public class HoldExpiryService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryService.class);

    /** 선점 반복 이탈 — 24시간 안 3번 만료면 24시간 제한(commerce.md 10-5) */
    public static final int BAN_THRESHOLD = 3;
    public static final Duration BAN_WINDOW = Duration.ofHours(24);
    public static final Duration BAN_DURATION = Duration.ofHours(24);

    private final ParticipationStore participations;
    private final GroupBuyQtyService qty;
    private final PaymentOrderService paymentOrders;
    private final MemberRestrictionService restrictions;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate separate;
    private final Clock clock;

    public HoldExpiryService(ParticipationStore participations, GroupBuyQtyService qty, PaymentOrderService paymentOrders, MemberRestrictionService restrictions,
                             SnowflakeIdGenerator ids, ApplicationEventPublisher events, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.participations = participations;
        this.qty = qty;
        this.paymentOrders = paymentOrders;
        this.restrictions = restrictions;
        this.ids = ids;
        this.events = events;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 결제 중 주문 하나를 닫는다(새 트랜잭션).
     *
     * @param requireDue 선점 시간이 지났을 때만(선점 만료 워커·조회 때 정리) — 중단·마감으로 닫을 때는 false
     * @return 이번에 닫았으면 true(이미 결제됐거나 다른 길이 닫았으면 false)
     */
    public boolean close(long participationId, ExpireCause cause, boolean requireDue) {
        Boolean closed = separate.execute(status -> {
            Instant now = now();
            paymentOrders.expireOpen(PaymentOrderType.GROUP_BUY, participationId, cause.name());
            Optional<ParticipationRow> found = participations.find(participationId);
            if (found.isEmpty() || !participations.expire(participationId, cause, requireDue ? now : null, now)) {
                return false;
            }
            ParticipationRow p = found.get();
            qty.release(p.groupBuyId(), p.id(), participations.items(p.id()), now);
            participations.insertHistory(ids.nextId(), p, ParticipationStatus.PENDING_PAYMENT, ParticipationStatus.EXPIRED, cause.name(), null, now);
            if (cause == ExpireCause.HOLD_TIMEOUT) {
                events.publishEvent(new GroupBuyEvents.HoldExpired(p.id(), p.groupBuyId(), p.userId()));
                if (participations.holdTimeouts(p.userId(), now.minus(BAN_WINDOW)) >= BAN_THRESHOLD) {
                    restrictions.grantAuto(p.userId(), RestrictionType.GROUP_BUY_BAN, BAN_DURATION, RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED);
                }
            }
            return true;
        });
        return Boolean.TRUE.equals(closed);
    }

    /** 선점 시간이 지난 결제 중 주문을 닫는다(워커 한 바퀴의 한 단계) — 닫은 수 */
    public int expireOverdue(int limit) {
        int done = 0;
        for (long id : participations.overdueHolds(now(), limit)) {
            try {
                if (close(id, ExpireCause.HOLD_TIMEOUT, true)) {
                    done++;
                }
            } catch (RuntimeException e) {
                log.error("선점 만료 처리가 실패했다 participationId={} 원인={}", id, e.getClass().getSimpleName());
            }
        }
        return done;
    }

    /** 그 회원의 그 공구 주문 중 선점이 지난 것만 그 자리에서 닫는다(조회·주문 때 — 남의 주문은 워커가, 결정 85) */
    public void expireOwnOverdue(long userId, long groupBuyId) {
        List<Long> own = participations.ownOverdueHolds(userId, groupBuyId, now());
        for (long id : own) {
            close(id, ExpireCause.HOLD_TIMEOUT, true);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
