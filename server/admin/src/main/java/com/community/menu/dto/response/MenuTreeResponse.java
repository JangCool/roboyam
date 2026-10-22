package com.community.menu.dto.response;

import java.util.List;

/**
 * {@code GET·PUT /api/admin/menu-sets/{key}/tree} — admin-web {@code MenuTreeResponse}.
 *
 * @param version  저장할 때 그대로 돌려보낸다(불투명 문자열)
 * @param editable 이 운영자가 저장할 수 있는가({@code layout.settings})
 */
public record MenuTreeResponse(MenuSetView set, List<MenuTreeNodeView> nodes, String version, boolean editable) {
}
