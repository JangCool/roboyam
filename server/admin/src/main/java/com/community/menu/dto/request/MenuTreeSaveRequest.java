package com.community.menu.dto.request;

import java.util.List;

/**
 * {@code PUT /api/admin/menu-sets/{key}/tree} — 트리 전체 교체(요청에 없는 기존 노드는 지운다).
 *
 * @param version 트리를 받을 때의 {@code version} 그대로 — 다르면 409 {@code MENU_VERSION_CONFLICT}
 */
public record MenuTreeSaveRequest(String version, List<MenuNodeRequest> nodes) {
}
