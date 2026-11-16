package com.community.pointgame.dto.response;

/** 당첨 확인 링크 — 관리자 설정값(복권 사업자 이름 · {@code https://} 주소). 사이트는 당첨 번호를 만들거나 비교하지 않는다 */
public record LottoCheckLinkView(String providerName, String url) {
}
