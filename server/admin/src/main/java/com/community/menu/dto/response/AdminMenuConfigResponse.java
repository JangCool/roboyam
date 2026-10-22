package com.community.menu.dto.response;

import java.util.List;

/**
 * {@code GET·PUT /api/admin/admin-menu} — admin-web {@code AdminMenuConfigResponse}. 두 모드를 한 번에.
 *
 * @param version  저장할 때 그대로 돌려보낸다(불투명 문자열)
 * @param editable 이 운영자가 저장할 수 있는가({@code admin-menu.manage})
 */
public record AdminMenuConfigResponse(Modes modes, String version, boolean editable) {

    public record Modes(List<AdminMenuNodeView> normal, List<AdminMenuNodeView> settings) {
    }
}
