package com.community.jjal.dto.request;

import java.util.List;

/** {@code PUT /api/me/jjals/order} — 보관함 전체 짤 공개 ID 를 새 순서로 한 번에(내려간 짤 포함). 지금 보관함과 빠짐·겹침 없이 같아야 한다 */
public record JjalOrderRequest(List<String> ids) {
}
