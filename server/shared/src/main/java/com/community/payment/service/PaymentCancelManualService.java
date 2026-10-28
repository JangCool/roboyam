package com.community.payment.service;

import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ManualCancelMethod;
import com.community.payment.dto.ManualCancelResolution;
import com.community.payment.exception.PaymentCancelNotFoundException;
import com.community.payment.exception.PaymentCancelNotGaveUpException;
import com.community.payment.handler.PaymentOrderHandlers;
import com.community.payment.repository.master.PaymentCancelStore;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멈춘(GAVE_UP) 취소의 「수동 처리 완료」 한 길(2a-3 + 공구 계약 결정 16·74) — 관리자 결제 화면({@link PaymentAdminActionService#manualCancel})과 공구의 계좌 송금 전환이 같이 쓴다.
 * 잠그는 차례는 결제 주문 행 → 취소 줄(구현 때 정한 것 4). 부르는 쪽 트랜잭션에 들어간다(없으면 새로).
 *
 * <ul>
 *   <li>방법 없음: 표시만 — 취소 누계 불변(2a-3 그대로).</li>
 *   <li>{@link ManualCancelMethod#CONSOLE_CANCEL}: 콘솔 취소 ID 를 붙이고 <b>취소 누계까지 올린다</b>(SUCCEEDED 와 같게 — 대사 이슈가 매일 다시 열리지 않게).</li>
 *   <li>{@link ManualCancelMethod#BANK_TRANSFER}: 누계는 그대로(계좌로 돌려준다 — 불변식·대사가 따로 뺀다).</li>
 * </ul>
 * 끝으로 처리기 {@code onCancelResolvedManually} 를 같은 트랜잭션에서 부른다(결제 모듈은 여전히 도메인을 모른다).
 */
@Service
public class PaymentCancelManualService {

    private final PaymentOrderStore orders;
    private final PaymentCancelStore cancels;
    private final PaymentEventStore events;
    private final PaymentOrderHandlers handlers;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public PaymentCancelManualService(PaymentOrderStore orders, PaymentCancelStore cancels, PaymentEventStore events, PaymentOrderHandlers handlers, SnowflakeIdGenerator ids,
                                      ObjectProvider<Clock> clocks) {
        this.orders = orders;
        this.cancels = cancels;
        this.events = events;
        this.handlers = handlers;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 수동 처리 완료.
     *
     * @param pgCancelId 콘솔 취소면 포트원 취소 ID(필수 아님 — 있으면 붙인다)
     * @return 닫기 전 취소 줄
     * @throws PaymentCancelNotFoundException  없음
     * @throws PaymentCancelNotGaveUpException GAVE_UP 이 아님(409)
     */
    @Transactional
    public CancelRow resolve(long cancelId, ManualCancelMethod method, String pgCancelId, String note, long operatorId) {
        CancelRow found = cancels.findById(cancelId).orElseThrow(PaymentCancelNotFoundException::new);
        orders.lockById(found.paymentOrderId()).orElseThrow(PaymentCancelNotFoundException::new);
        CancelRow cancel = cancels.lockById(cancelId).orElseThrow(PaymentCancelNotFoundException::new);
        if (cancel.status() != CancelStatus.GAVE_UP) {
            throw new PaymentCancelNotGaveUpException();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String pgId = pgCancelId == null || pgCancelId.isBlank() ? null : pgCancelId.strip();
        cancels.markManual(cancelId, operatorId, note, method, method == ManualCancelMethod.CONSOLE_CANCEL ? pgId : null, now);
        if (method == ManualCancelMethod.CONSOLE_CANCEL && !orders.addCanceled(cancel.paymentOrderId(), cancel.amount(), now)) {
            throw new IllegalStateException("취소 누계가 결제 금액을 넘는다 — 취소 대기열과 결제 주문이 어긋났다");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("cancelId", Long.toString(cancelId));
        detail.put("kind", cancel.kind().name());
        detail.put("amount", cancel.amount());
        if (method != null) {
            detail.put("method", method.name());
        }
        events.insert(ids.nextId(), cancel.paymentOrderId(), PaymentEventStore.Kind.CANCEL_MANUAL, detail, now);
        OrderRow order = orders.findById(cancel.paymentOrderId()).orElseThrow();
        handlers.require(order.orderType()).onCancelResolvedManually(new ManualCancelResolution(order.id(), order.orderType(), order.orderRefId(), order.userId(),
                cancelId, cancel.kind(), cancel.amount(), method, pgId, operatorId, now));
        return cancel;
    }
}
