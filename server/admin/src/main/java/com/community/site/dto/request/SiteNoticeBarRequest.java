package com.community.site.dto.request;

/**
 * 공지 띠 저장 요청 — 출처가 아닌 쪽의 칸도 그대로 저장한다(출처를 바꿔 볼 때 지운 듯 보이지 않게). 검사 문구가 칸별로 나가므로 서비스가 한꺼번에 검사한다.
 *
 * @param enabled    띠를 보일지(필수)
 * @param source     {@code BOARD} · {@code CUSTOM}
 * @param boardCount 5 또는 10
 * @param customText {@code CUSTOM} 이면 필수 — 앞뒤 공백을 뗀 뒤 1~100자, {@code <}·{@code >} 없음. 비우면 없음
 * @param customLink 선택 — {@code /…}(사이트 안) 또는 {@code http(s)://…}, 500자 이하. 비우면 없음
 */
public record SiteNoticeBarRequest(Boolean enabled, String source, Integer boardCount, String customText, String customLink) {
}
