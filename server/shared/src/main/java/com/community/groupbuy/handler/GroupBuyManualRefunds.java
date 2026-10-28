package com.community.groupbuy.handler;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.repository.master.BankTransferStore;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.payment.dto.ManualCancelMethod;
import com.community.payment.dto.ManualCancelResolution;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 운영자가 멈춘 취소를 직접 처리했을 때 공구 환불 기록을 맞춘다(계약 결정 16·67·74) — 두 공구 결제 처리기(주문·반품비)가 같이 쓴다. 결제 모듈의 수동 처리 트랜잭션 안.
 *
 * <ul>
 *   <li>콘솔 취소·표시만: 환불 기록 MANUAL(완료 시각) + 「환불 처리됨(운영자 확인)」 이벤트.</li>
 *   <li>계좌 송금: 기록의 방법을 BANK_TRANSFER 로(진행 중 그대로 — 송금 완료 때 완료) + 송금 대기 줄(계좌 받기 전).</li>
 * </ul>
 */
@Component
public class GroupBuyManualRefunds {

    private final GroupBuyRefundStore refunds;
    private final BankTransferStore transfers;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;

    public GroupBuyManualRefunds(GroupBuyRefundStore refunds, BankTransferStore transfers, SnowflakeIdGenerator ids, ApplicationEventPublisher events) {
        this.refunds = refunds;
        this.transfers = transfers;
        this.ids = ids;
        this.events = events;
    }

    public void resolve(ManualCancelResolution r) {
        if (r.method() == ManualCancelMethod.BANK_TRANSFER) {
            refunds.switchToBankByCancel(r.cancelId()).ifPresent(refund -> transfers.insert(ids.nextId(), "GROUP_BUY_REFUND", refund.id(), refund.participationId(),
                    refund.userId(), refund.amount(), "PG 취소 불가 — 계좌 송금 전환", r.operatorId(), r.resolvedAt()));
            return;
        }
        refunds.markManualByCancel(r.cancelId(), r.resolvedAt()).ifPresent(refund -> events.publishEvent(new GroupBuyEvents.RefundResolvedManually(refund.id(),
                refund.participationId(), refund.userId(), r.method() == null ? "MANUAL" : r.method().name())));
    }
}
