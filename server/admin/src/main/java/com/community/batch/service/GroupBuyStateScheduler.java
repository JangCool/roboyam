package com.community.batch.service;

import com.community.batch.config.BatchProperties;
import com.community.batch.core.WorkerHeartbeats;
import com.community.groupbuy.service.GroupBuyRefundWorker;
import com.community.groupbuy.service.GroupBuyFulfillmentWorker;
import com.community.groupbuy.service.GroupBuyStateWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공구 워커 둘의 「언제 돌릴지」(관리자 서버 쪽, docs/domain/commerce.md 15-4·15-5 「공구 상태 전이·선점 만료 — 1분」, commerce-groupbuy-contract 결정 61) —
 * 상태 워커({@value GroupBuyStateWorker#WORKER_KEY} — 선점 만료·모집 시작·마감·연장·판정·완료)와 불발·중단 환불 워커({@value GroupBuyRefundWorker#WORKER_KEY})를 따로 돈다
 * (대량 환불이 마감·만료를 막지 않게). 한 바퀴의 일은 shared 의 워커가 한다 — 배치 서버로 뗄 때 이 클래스만 옮긴다(15-2). {@code PaymentCancelScheduler} 와 같은 모양이다.
 *
 * <ul>
 *   <li>{@code roboyam.batch.enabled=false} 인 서버는 돌지 않는다(선점 만료는 회원이 주문할 때 본인 것을 그 자리에서 정리하므로 워커가 잠시 멈춰도 판매가 틀어지지 않는다 — 15-4).</li>
 *   <li>바퀴마다 워커 기록({@code worker_heartbeats}) — 일이 없으면 간격마다 한 번만, 실패하면 가린 오류 한 줄.</li>
 * </ul>
 */
@Component
public class GroupBuyStateScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyStateScheduler.class);

    private final BatchProperties properties;
    private final GroupBuyStateWorker stateWorker;
    private final GroupBuyRefundWorker refundWorker;
    private final GroupBuyFulfillmentWorker fulfillmentWorker;
    private final WorkerHeartbeats heartbeats;

    public GroupBuyStateScheduler(BatchProperties properties, GroupBuyStateWorker stateWorker, GroupBuyRefundWorker refundWorker, GroupBuyFulfillmentWorker fulfillmentWorker,
                                  WorkerHeartbeats heartbeats) {
        this.properties = properties;
        this.stateWorker = stateWorker;
        this.refundWorker = refundWorker;
        this.fulfillmentWorker = fulfillmentWorker;
        this.heartbeats = heartbeats;
    }

    /** @return 이번 바퀴에 다룬 건수(배치를 끈 서버면 -1) */
    @Scheduled(initialDelayString = "${roboyam.groupbuy.state-poll-millis:60000}", fixedDelayString = "${roboyam.groupbuy.state-poll-millis:60000}")
    public int stateTick() {
        if (!properties.enabled()) {
            return -1;
        }
        try {
            int handled = stateWorker.runOnce();
            heartbeats.success(GroupBuyStateWorker.WORKER_KEY, handled);
            return handled;
        } catch (RuntimeException e) {
            log.error("공구 상태 워커 한 바퀴가 실패했다 원인={}", e.getClass().getSimpleName());
            heartbeats.failure(GroupBuyStateWorker.WORKER_KEY, e);
            return 0;
        }
    }

    /** @return 이번 바퀴에 다룬 주문 수(배치를 끈 서버면 -1) */
    @Scheduled(initialDelayString = "${roboyam.groupbuy.refund-poll-millis:60000}", fixedDelayString = "${roboyam.groupbuy.refund-poll-millis:60000}")
    public int refundTick() {
        if (!properties.enabled()) {
            return -1;
        }
        try {
            int handled = refundWorker.runOnce();
            heartbeats.success(GroupBuyRefundWorker.WORKER_KEY, handled);
            return handled;
        } catch (RuntimeException e) {
            log.error("공구 환불 워커 한 바퀴가 실패했다 원인={}", e.getClass().getSimpleName());
            heartbeats.failure(GroupBuyRefundWorker.WORKER_KEY, e);
            return 0;
        }
    }

    /** 발송·요청 워커(에스크로 대기열·부족분 미결제 요청 닫기·공급 불가 기본 선택 — 2b-3). @return 다룬 건수(배치를 끈 서버면 -1) */
    @Scheduled(initialDelayString = "${roboyam.groupbuy.fulfillment-poll-millis:60000}", fixedDelayString = "${roboyam.groupbuy.fulfillment-poll-millis:60000}")
    public int fulfillmentTick() {
        if (!properties.enabled()) {
            return -1;
        }
        try {
            int handled = fulfillmentWorker.runOnce();
            heartbeats.success(GroupBuyFulfillmentWorker.WORKER_KEY, handled);
            return handled;
        } catch (RuntimeException e) {
            log.error("공구 발송·요청 워커 한 바퀴가 실패했다 원인={}", e.getClass().getSimpleName());
            heartbeats.failure(GroupBuyFulfillmentWorker.WORKER_KEY, e);
            return 0;
        }
    }
}
