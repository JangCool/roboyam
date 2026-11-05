package com.community.emoticon.dto.request;

/**
 * 심사 승인({@code { price }} — 0 = 무료 또는 가격 단계) · 가격 바꾸기({@code { price, reason }} — S2) · 반려 · 판매 중단 · 정산 계좌 원문 보기({@code { reason }} — 5~200자) 본문. 검사는 서비스가 한다.
 */
public record EmoticonReviewDecisionRequest(Integer price, String reason) {
}
