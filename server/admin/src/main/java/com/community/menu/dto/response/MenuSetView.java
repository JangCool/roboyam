package com.community.menu.dto.response;

import java.time.Instant;

/**
 * 메뉴 그룹 하나 — admin-web {@code MenuSet}.
 *
 * @param system    기본 그룹 — 지우거나 위치를 바꿀 수 없다
 * @param nodeCount 트리의 노드 수(하위 포함)
 */
public record MenuSetView(String key, String name, String nameEn, String placement, boolean enabled, String description, boolean system, long nodeCount,
                          Instant updatedAt) {
}
