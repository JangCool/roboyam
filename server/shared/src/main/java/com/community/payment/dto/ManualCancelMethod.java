package com.community.payment.dto;

/**
 * 멈춘 취소를 운영자가 실제로 어떻게 처리했는가({@code payment_cancels.manual_method}, 공구 계약 결정 74). 이름은 DB 값이다.
 *
 * <ul>
 *   <li>{@link #CONSOLE_CANCEL}: 포트원 콘솔에서 직접 취소했다 — 그 취소 ID 를 줄에 붙이고 결제 주문의 취소 누계까지 올린다(SUCCEEDED 와 같게 — 대사 이슈가 다시 열리지 않게).</li>
 *   <li>{@link #BANK_TRANSFER}: 계좌로 송금한다(결정 67) — PG 취소 누계는 그대로, 불변식·대사는 이 몫을 따로 뺀다.</li>
 * </ul>
 * 방법 없이 닫으면(null) 표시만이다(2a-3 「수동 처리 완료」).
 */
public enum ManualCancelMethod {
    CONSOLE_CANCEL,
    BANK_TRANSFER
}
