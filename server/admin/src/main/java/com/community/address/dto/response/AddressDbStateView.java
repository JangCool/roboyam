package com.community.address.dto.response;

import java.time.Instant;

/**
 * 지금 올라 있는 주소 DB(가장 최근에 끝난 적재) — 한 번도 올린 적이 없으면 {@code loadedAt} 이 null.
 *
 * @param sourceLabel  올린 파일 이름(여럿이면 쉼표)
 * @param englishCount 영문 주소가 붙은 줄 수(영문 파일이 없었으면 0)
 * @param stale        마지막 적재가 갱신 권고 일수보다 오래됐다(올린 적이 없어도 true)
 */
public record AddressDbStateView(Instant loadedAt, String sourceLabel, Long entryCount, Long englishCount, boolean stale) {
}
