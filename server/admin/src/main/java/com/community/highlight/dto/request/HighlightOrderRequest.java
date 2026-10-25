package com.community.highlight.dto.request;

import java.util.List;

/** {@code PUT /api/admin/highlights/order} — 전체 하이라이트의 새 순서(ID 집합이 지금과 정확히 같아야 한다) */
public record HighlightOrderRequest(List<String> ids) {
}
