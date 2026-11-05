package com.community.emoticon.dto.request;

/**
 * 유료 팩 결제 준비({@code POST /api/emoticons/packs/{id}/purchase}) 본문 — 환불 규칙 고지 동의(「한 번이라도 쓰면 환불할 수 없어요 · 7일 안에 쓰지 않으면 환불할 수 있어요」,
 * commerce.md 7-3). {@code true} 가 아니면 {@code 400 fields.agreeRefundNotice}. 동의 시각은 구매 기록에 남는다.
 */
public record EmoticonPurchaseRequest(Boolean agreeRefundNotice) {
}
