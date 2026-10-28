package com.community.groupbuy.dto;

/**
 * 추가 차수 공구(결정 31) — 복사한 설명에 이전 차수의 날짜·수량·배송비 문구가 남았는지 확인하라는 경고를 늘 띄운다({@code reviewWarning}).
 */
public record RoundCreated(long groupBuyId, long roundRootId, int roundNo, boolean reviewWarning) {
}
