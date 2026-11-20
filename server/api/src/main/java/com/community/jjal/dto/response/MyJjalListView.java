package com.community.jjal.dto.response;

import java.util.List;

/**
 * {@code GET /api/me/jjals}(user-web {@code MyJjalListResponse}).
 *
 * @param items             회원이 정한 순서(내려간 짤 포함)
 * @param count             보관함 개수(내려간 짤 포함 — 상한에 센다)
 * @param limit             상한
 * @param introAcknowledged 처음 안내를 확인했는가(계정에 기록)
 */
public record MyJjalListView(List<MyJjalItemView> items, int count, int limit, boolean introAcknowledged) {
}
