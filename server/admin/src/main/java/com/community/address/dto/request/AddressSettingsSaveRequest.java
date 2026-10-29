package com.community.address.dto.request;

/**
 * 주소 검색 설정 저장 본문 — 검사는 서비스가 칸마다 모아 한다({@code fields.provider}·{@code fields.scriptUrl}·{@code fields.staleWarnDays}).
 *
 * @param provider      {@code DAUM} | {@code INTERNAL} | {@code DAUM_FALLBACK_INTERNAL}
 * @param scriptUrl     다음 스크립트 주소 — null·빈 글자면 공식 주소로 돌린다
 * @param staleWarnDays 갱신 권고 일수(7~365)
 */
public record AddressSettingsSaveRequest(String provider, String scriptUrl, Integer staleWarnDays) {
}
