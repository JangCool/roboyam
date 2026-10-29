package com.community.address.dto.response;

import java.util.List;

/** 적재 이력 한 쪽 — 최신이 위, 쪽 번호는 0부터 */
public record AddressDbLoadPage(List<AddressDbLoadRowView> items, long total, int page, int size) {
}
