package com.community.group.dto.request;

import java.util.List;

/** {@code PUT /api/admin/group-categories/order} — 모든 분류 ID 의 새 순서(한 번씩). 아니면 400 {@code fields.ids} */
public record GroupCategoryOrderRequest(List<String> ids) {
}
