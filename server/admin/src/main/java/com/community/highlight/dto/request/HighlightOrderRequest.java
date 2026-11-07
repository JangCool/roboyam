package com.community.highlight.dto.request;

import java.util.List;

/**
 * {@code PUT /api/admin/highlights/order} — 한 곳의 하이라이트 새 순서(그 곳의 ID 집합과 정확히 같아야 한다).
 *
 * @param place 놓일 곳 키(비우면 home)
 */
public record HighlightOrderRequest(String place, List<String> ids) {
}
