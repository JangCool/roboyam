package com.community.payment.handler;

import com.community.payment.dto.CanceledPayment;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;

/**
 * 결제 결과를 도메인에 넘기는 자리 — 결제 모듈이 정의하고 각 도메인(충전·공구·시험 주문)이 구현한다(commerce.md 3절 「결제 결과를 도메인에 넘기는 방법」). 결제 모듈은 도메인을 모른다.
 *
 * <ul>
 *   <li>{@link #onPaid}: 결제 확정과 <b>같은 트랜잭션</b>에서 동기로 불린다(도메인 확정 — 충전 적립·참여 JOINED). 거절하면 결제 모듈이 전액 자동 취소를 넣는다.
 *       <b>예외를 던지면 확정 전체가 되돌려진다</b>(READY 그대로, 취소 없음 — 다음 웹훅·READY 정리가 다시 시도, 계약 「확정 흐름」 3). 늦은 결제·금액 불일치는 부르지 않는다.</li>
 *   <li>{@link #onCanceled}: 취소 한 건이 포트원에서 성공해 누계에 반영된 트랜잭션에서 불린다(취소 한 건마다 한 번). 자동 취소 안내 같은 회원 알림·메일은 도메인이 여기서 보낸다(결정 11).</li>
 * </ul>
 * 알림·메일은 커밋 뒤 이벤트·메일 대기열로 보낸다(트랜잭션 안에서 외부를 부르지 않는다).
 */
public interface PaymentOrderHandler {

    /** 맡은 주문 종류 — 종류마다 처리기는 하나다({@link PaymentOrderHandlers}) */
    PaymentOrderType orderType();

    PaymentDecision onPaid(PaidPayment payment);

    void onCanceled(CanceledPayment cancellation);
}
