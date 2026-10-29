package com.community.address.dto.response;

import java.util.List;

/**
 * 자체 주소 DB 검색 한 쪽 — 쪽 번호는 0부터.
 *
 * @param total 맞는 줄 수. {@code AddressSearchPolicy.TOTAL_CAP} 까지만 센다(넘으면 그 값 — 화면은 「더 자세히」를 권한다)
 */
public record AddressSearchPageResponse(List<AddressSearchResultResponse> items, long total, int page, int size) {
}
