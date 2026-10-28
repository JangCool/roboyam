package com.community.groupbuy.service;

import com.community.groupbuy.entity.ExpireCause;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RefundCause;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 불발·중단 환불 워커의 한 바퀴({@value #WORKER_KEY} — 계약 결정 29 ②·37·61·69) — 「언제 돌릴지」(1분)는 관리자 서버의 {@code GroupBuyRefundScheduler}.
 * 상태 워커와 따로 둬서 대량 환불이 마감·만료 처리를 막지 않고, 중간에 멈춘 일도 다음 바퀴가 이어 받는다(「아직 처리 안 한 것 전부」).
 *
 * <ul>
 *   <li>대상: 불발(CLOSED_FAILED)·중단(CANCELED) 공구의 비종결 주문 — 바퀴당 {@value #BATCH_LIMIT}건.</li>
 *   <li>주문마다 <b>지금 상태를 다시 읽고</b> 분기한다: 결제 중이면 결제 행 먼저 닫고 만료(중단 {@code GROUP_BUY_CANCELED} · 불발 {@code PAYMENT_FAILED_CLOSED} — 이탈로 세지 않는다),
 *       그새 결제가 확정됐으면(닫기에 졌으면) 다시 읽어 환불로. 발송 전 결제된 주문(JOINED·CONFIRMED·PREPARING)은 REFUNDED + 남은 줄 CANCEL + 남은 결제액 전부 취소 대기열(주문 행을
 *       {@code SKIP LOCKED} 로 잠근다 — 잠겨 있으면 다음 바퀴). 이미 발송된 주문은 그대로(정상 흐름 — 반품은 요청으로).</li>
 *   <li>조건부 전이라 몇 번을 돌려도 주문마다 환불은 한 번이다(G24).</li>
 * </ul>
 */
@Component
public class GroupBuyRefundWorker {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyRefundWorker.class);

    public static final String WORKER_KEY = "groupbuy-refund";
    /** 바퀴당 상한 — 즉시 PG 시도는 결제 모듈이 커밋 뒤에 하므로 이 상한으로 한 바퀴의 시간을 묶는다(결정 61) */
    public static final int BATCH_LIMIT = 50;

    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final HoldExpiryService holds;
    private final ParticipationCancelService cancels;

    public GroupBuyRefundWorker(ParticipationStore participations, GroupBuyStore groups, HoldExpiryService holds, ParticipationCancelService cancels) {
        this.participations = participations;
        this.groups = groups;
        this.holds = holds;
        this.cancels = cancels;
    }

    /** 한 바퀴 — 다룬 주문 수 */
    public int runOnce() {
        int handled = 0;
        for (long id : participations.unsettledInClosedGroups(BATCH_LIMIT)) {
            try {
                if (settle(id)) {
                    handled++;
                }
            } catch (RuntimeException e) {
                log.error("공구 불발·중단 환불이 실패했다 — 다음 바퀴에 다시 participationId={} 원인={}", id, e.getClass().getSimpleName());
            }
        }
        return handled;
    }

    /** 주문 하나 — 닫거나 환불했으면 true */
    public boolean settle(long participationId) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Optional<ParticipationRow> found = participations.find(participationId);
            if (found.isEmpty()) {
                return false;
            }
            ParticipationRow p = found.get();
            GroupBuyStatus groupStatus = groups.find(p.groupBuyId()).orElseThrow().status();
            if (groupStatus != GroupBuyStatus.CLOSED_FAILED && groupStatus != GroupBuyStatus.CANCELED) {
                return false;
            }
            boolean canceled = groupStatus == GroupBuyStatus.CANCELED;
            if (p.status() == ParticipationStatus.PENDING_PAYMENT) {
                if (holds.close(p.id(), canceled ? ExpireCause.GROUP_BUY_CANCELED : ExpireCause.PAYMENT_FAILED_CLOSED, false)) {
                    return true;
                }
                continue; // 닫기에 졌다 — 그새 결제가 확정됐으면 환불로
            }
            if (ParticipationStatus.PAID_BEFORE_SHIPPING.contains(p.status())) {
                return cancels.refundWhole(p.id(), canceled ? RefundCause.CANCELED : RefundCause.FAILED);
            }
            return false;
        }
        return false;
    }
}
