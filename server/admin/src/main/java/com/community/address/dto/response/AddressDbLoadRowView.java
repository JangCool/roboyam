package com.community.address.dto.response;

import java.time.Instant;

/**
 * 적재 이력 한 줄(끝난 적재).
 *
 * @param loadedAt 적재가 끝난 시각
 * @param loadedBy 올린 운영자 이름(지워졌으면 null)
 */
public record AddressDbLoadRowView(String id, Instant loadedAt, String sourceLabel, long entryCount, long englishCount, String loadedBy) {
}
